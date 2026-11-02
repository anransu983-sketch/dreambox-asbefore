package com.suanran.pack.unlock;

import android.content.Context;

import com.suanran.dreambox.core.pack.PackBridgeDispatcher;
import com.suanran.dreambox.core.pack.PackCallback;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * 流媒体解锁检测插件入口（2026-11-03 从本体 dashboard 拆出，检测逻辑 1:1 移植）：
 * 经本机 mihomo mixed 端口 127.0.0.1:7890 发请求，测的是当前节点出口的解锁情况。
 * 平台清单放包里，后续加平台只更新插件、不用本体发版。
 */
public class UnlockPackEntry implements PackBridgeDispatcher {

    private static final Proxy PROXY =
            new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7890));
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_BODY_BYTES = 512 * 1024;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final Pattern NETFLIX_REGION =
            Pattern.compile("netflix\\.com/([a-z]{2}(?:-[a-z]{2})?)/title", Pattern.CASE_INSENSITIVE);

    private final ExecutorService io = Executors.newCachedThreadPool();

    @Override
    public void init(Context context, Function3<? super String, ? super String, ? super String, Unit> emit) {
        // 无状态。
    }

    @Override
    public void release() {
        io.shutdownNow();
    }

    @Override
    public boolean dispatch(String pluginId, String method, String optionsJson, PackCallback callback) {
        if (!"Unlock".equals(pluginId)) return false;
        if (!"probe".equals(method)) return false;
        final String platform;
        try {
            platform = new JSONObject(optionsJson == null ? "{}" : optionsJson).optString("platform");
        } catch (Exception e) {
            callback.onResult(false, null, "参数解析失败");
            return true;
        }
        io.execute(() -> {
            JSONObject out = probe(platform);
            callback.onResult(true, out.toString(), null);
        });
        return true;
    }

    /** 返回 {status: unlocked|locked|failed, region: "JP"|null}，异常一律 failed 不崩。 */
    private JSONObject probe(String platform) {
        JSONObject out = new JSONObject();
        try {
            switch (platform) {
                case "NETFLIX":
                    return probeNetflix();
                case "DISNEY_PLUS":
                    return probeDisneyPlus();
                case "YOUTUBE_PREMIUM":
                    return probeYouTubePremium();
                default:
                    out.put("status", "failed");
                    return out;
            }
        } catch (Exception e) {
            try {
                out.put("status", "failed");
            } catch (Exception ignored) {
            }
            return out;
        }
    }

    private JSONObject result(String status, String region) throws Exception {
        JSONObject out = new JSONObject();
        out.put("status", status);
        if (region != null) out.put("region", region);
        return out;
    }

    private HttpURLConnection openConnection(String url, boolean followRedirects) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection(PROXY);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setInstanceFollowRedirects(followRedirects);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        return conn;
    }

    private String readBodyCapped(HttpURLConnection conn) throws Exception {
        InputStream stream;
        try {
            stream = conn.getInputStream();
        } catch (Exception e) {
            stream = conn.getErrorStream();
            if (stream == null) throw e;
        }
        byte[] buffer = new byte[MAX_BODY_BYTES + 1];
        int total = 0;
        try {
            while (total <= MAX_BODY_BYTES) {
                int n = stream.read(buffer, total, buffer.length - total);
                if (n < 0) break;
                total += n;
            }
        } finally {
            stream.close();
        }
        return new String(buffer, 0, total, StandardCharsets.UTF_8);
    }

    /** Netflix：不跟随跳转，看 Location 头；含 title 且非 notAvailable → 解锁，顺带解析地区码。 */
    private JSONObject probeNetflix() throws Exception {
        HttpURLConnection conn = openConnection("https://www.netflix.com/title/80018499", false);
        try {
            int code = conn.getResponseCode();
            String location = conn.getHeaderField("Location");
            if (location == null) location = "";
            String lower = location.toLowerCase();
            boolean isTitleRedirect = lower.contains("netflix.com/title");
            boolean isNotAvailable = lower.contains("notavailable");
            if (code == 200) {
                return result("unlocked", null);
            }
            if (code >= 300 && code <= 399 && isTitleRedirect && !isNotAvailable) {
                String region = null;
                Matcher m = NETFLIX_REGION.matcher(location);
                if (m.find()) region = m.group(1).toUpperCase();
                return result("unlocked", region);
            }
            if (code >= 300 && code <= 399 && isNotAvailable) {
                return result("locked", null);
            }
            return result("failed", null);
        } finally {
            conn.disconnect();
        }
    }

    /** Disney+：跟随跳转，200 且含 disneyplus 且无不可用关键字 → 解锁。 */
    private JSONObject probeDisneyPlus() throws Exception {
        HttpURLConnection conn = openConnection("https://www.disneyplus.com", true);
        try {
            if (conn.getResponseCode() != 200) return result("failed", null);
            String body = readBodyCapped(conn).toLowerCase();
            boolean unlocked = body.contains("disneyplus")
                    && !body.contains("not available")
                    && !body.contains("unavailable");
            return result(unlocked ? "unlocked" : "locked", null);
        } finally {
            conn.disconnect();
        }
    }

    /** YouTube Premium：内容含 Premium 且不含 not available → 解锁。 */
    private JSONObject probeYouTubePremium() throws Exception {
        HttpURLConnection conn = openConnection("https://www.youtube.com/premium", true);
        try {
            if (conn.getResponseCode() != 200) return result("failed", null);
            String body = readBodyCapped(conn);
            boolean unlocked = body.contains("Premium") && !body.contains("not available");
            return result(unlocked ? "unlocked" : "locked", null);
        } finally {
            conn.disconnect();
        }
    }
}
