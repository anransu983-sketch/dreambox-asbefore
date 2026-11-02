package com.suanran.pack.webdav;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.suanran.dreambox.core.pack.PackBridgeDispatcher;
import com.suanran.dreambox.core.pack.PackCallback;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.SSLSocketFactory;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * WebDAV 云备份插件入口（2026-11-03 从本体 Meta 功能拆出）：
 * 配置在本包加密存储（AndroidKeyStore AES-256-GCM）；备份 zip 的生成/恢复走宿主
 * HostBackup 桥（pack.json 声明 hostBackup），本类只管 WebDAV 协议本身
 * （原生 MetaWebDavBackup 的裸 socket 客户端移植：HttpURLConnection 不支持 PROPFIND/MKCOL）。
 */
public class WebDavPackEntry implements PackBridgeDispatcher {

    private static final String KEY_ALIAS = "webdav.store.v1";
    private static final String HOST_BACKUP_FILE = "host-backup.zip";
    private static final String EMPTY_XML = "<?xml version=\"1.0\"?><propfind xmlns=\"DAV:\"><prop><displayname/><getlastmodified/><resourcetype/></prop></propfind>";

    private Context appContext;
    private final ExecutorService io = Executors.newCachedThreadPool();

    @Override
    public void init(Context context, Function3<? super String, ? super String, ? super String, Unit> emit) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public void release() {
        io.shutdownNow();
    }

    @Override
    public boolean dispatch(String pluginId, String method, String optionsJson, PackCallback callback) {
        if (!"WebDav".equals(pluginId)) return false;
        final JSONObject opts;
        try {
            opts = new JSONObject(optionsJson == null ? "{}" : optionsJson);
        } catch (Exception e) {
            callback.onResult(false, null, "参数解析失败");
            return true;
        }
        switch (method) {
            case "getConfig":
                io.execute(() -> {
                    try {
                        JSONObject cfg = loadConfig();
                        JSONObject out = new JSONObject()
                                .put("url", cfg.optString("url"))
                                .put("account", cfg.optString("account"))
                                .put("directory", cfg.optString("directory"))
                                .put("hasPassword", !cfg.optString("password").isEmpty());
                        callback.onResult(true, out.toString(), null);
                    } catch (Exception e) {
                        callback.onResult(false, null, errText(e));
                    }
                });
                return true;
            case "saveConfig":
                io.execute(() -> {
                    try {
                        JSONObject old = loadConfig();
                        JSONObject cfg = new JSONObject()
                                .put("url", opts.optString("url").trim())
                                .put("account", opts.optString("account").trim())
                                .put("directory", opts.optString("directory").trim());
                        String pw = opts.optString("password");
                        // 密码留空 = 保持原密码（页面只显示是否已设置，不回显明文）。
                        cfg.put("password", pw.isEmpty() ? old.optString("password") : pw);
                        saveConfig(cfg);
                        callback.onResult(true, "{}", null);
                    } catch (Exception e) {
                        callback.onResult(false, null, errText(e));
                    }
                });
                return true;
            case "test":
                io.execute(() -> {
                    try {
                        WebDavClient client = new WebDavClient(requireConfig());
                        client.ensureRootDirectory();
                        client.listBackups();
                        callback.onResult(true, "{}", null);
                    } catch (Exception e) {
                        callback.onResult(false, null, errText(e));
                    }
                });
                return true;
            case "listBackups":
                io.execute(() -> {
                    try {
                        WebDavClient client = new WebDavClient(requireConfig());
                        List<BackupFile> files = client.listBackups();
                        JSONArray arr = new JSONArray();
                        for (BackupFile f : files) {
                            arr.put(new JSONObject().put("name", f.name).put("lastModified", f.lastModified));
                        }
                        callback.onResult(true, new JSONObject().put("files", arr).toString(), null);
                    } catch (Exception e) {
                        callback.onResult(false, null, errText(e));
                    }
                });
                return true;
            case "backupUpload":
                io.execute(() -> {
                    try {
                        File local = new File(packDir(), HOST_BACKUP_FILE);
                        if (!local.isFile()) throw new IllegalStateException("宿主备份文件不存在（先调 HostBackup.export）");
                        WebDavClient client = new WebDavClient(requireConfig());
                        client.ensureRootDirectory();
                        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
                        String remoteName = "FlyCat-backup-" + stamp + ".zip";
                        client.upload(remoteName, local);
                        //noinspection ResultOfMethodCallIgnored
                        local.delete();
                        callback.onResult(true, new JSONObject().put("name", remoteName).toString(), null);
                    } catch (Exception e) {
                        callback.onResult(false, null, errText(e));
                    }
                });
                return true;
            case "restorePrepare":
                io.execute(() -> {
                    try {
                        WebDavClient client = new WebDavClient(requireConfig());
                        client.ensureRootDirectory();
                        List<BackupFile> files = client.listBackups();
                        BackupFile latest = null;
                        for (BackupFile f : files) {
                            if (latest == null || f.lastModified > latest.lastModified) latest = f;
                        }
                        if (latest == null) throw new IllegalStateException("云端没有找到备份文件");
                        File target = new File(packDir(), HOST_BACKUP_FILE);
                        client.download(latest.name, target);
                        JSONObject out = new JSONObject().put("name", latest.name).put("lastModified", latest.lastModified);
                        callback.onResult(true, out.toString(), null);
                    } catch (Exception e) {
                        callback.onResult(false, null, errText(e));
                    }
                });
                return true;
            default:
                return false;
        }
    }

    private String errText(Exception e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private File packDir() {
        File dir = new File(appContext.getFilesDir(), "packs/webdav");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    // ── 加密配置存储（与 sshtty 同款：AES-256-GCM + AndroidKeyStore） ──

    private File storeFile() {
        File dir = new File(appContext.getFilesDir(), "webdav-data");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, "config.json.enc");
    }

    private SecretKey storeKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        KeyStore.Entry e = ks.getEntry(KEY_ALIAS, null);
        if (e instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) e).getSecretKey();
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }

    private synchronized JSONObject loadConfig() throws Exception {
        File f = storeFile();
        if (!f.exists()) return new JSONObject();
        byte[] all;
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            all = bos.toByteArray();
        }
        JSONObject env = new JSONObject(new String(all, StandardCharsets.UTF_8));
        byte[] iv = Base64.decode(env.getString("iv"), Base64.NO_WRAP);
        byte[] ct = Base64.decode(env.getString("ct"), Base64.NO_WRAP);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, storeKey(), new GCMParameterSpec(128, iv));
        return new JSONObject(new String(c.doFinal(ct), StandardCharsets.UTF_8));
    }

    private synchronized void saveConfig(JSONObject cfg) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, storeKey());
        byte[] ct = c.doFinal(cfg.toString().getBytes(StandardCharsets.UTF_8));
        JSONObject env = new JSONObject()
                .put("iv", Base64.encodeToString(c.getIV(), Base64.NO_WRAP))
                .put("ct", Base64.encodeToString(ct, Base64.NO_WRAP));
        try (OutputStream out = new FileOutputStream(storeFile())) {
            out.write(env.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private JSONObject requireConfig() throws Exception {
        JSONObject cfg = loadConfig();
        if (cfg.optString("url").isEmpty() || cfg.optString("account").isEmpty()
                || cfg.optString("password").isEmpty()) {
            throw new IllegalStateException("请先填写并保存 WebDAV 配置");
        }
        return cfg;
    }

    // ── WebDAV 裸 socket 客户端（移植自本体 MetaWebDavBackup） ──

    private static class BackupFile {
        final String name;
        final long lastModified;
        BackupFile(String name, long lastModified) {
            this.name = name;
            this.lastModified = lastModified;
        }
    }

    private static class WebDavClient {
        private final String rootUrl;
        private final String authHeader;
        private final int socketTimeout = 120_000;

        WebDavClient(JSONObject cfg) {
            String base = cfg.optString("url").trim();
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            String dir = cfg.optString("directory").trim();
            while (dir.startsWith("/")) dir = dir.substring(1);
            while (dir.endsWith("/")) dir = dir.substring(0, dir.length() - 1);
            this.rootUrl = (dir.isEmpty() ? base : base + "/" + dir) + "/";
            String pair = cfg.optString("account").trim() + ":" + cfg.optString("password");
            this.authHeader = "Basic " + java.util.Base64.getEncoder()
                    .encodeToString(pair.getBytes(StandardCharsets.UTF_8));
        }

        void ensureRootDirectory() throws Exception {
            try {
                rawRequest("MKCOL", rootUrl, null, null, null);
            } catch (Exception ignored) {
                // MKCOL 失败可能只是目录已存在，继续用 PROPFIND 验证。
            }
            HttpResponse resp = rawRequest("PROPFIND", rootUrl, EMPTY_XML.getBytes(StandardCharsets.UTF_8),
                    null, new String[][]{{"Depth", "0"}});
            if (resp.statusCode != 200 && resp.statusCode != 207) {
                throw new IllegalStateException("WebDAV 目录不可访问：HTTP " + resp.statusCode);
            }
        }

        List<BackupFile> listBackups() throws Exception {
            HttpResponse resp = rawRequest("PROPFIND", rootUrl, EMPTY_XML.getBytes(StandardCharsets.UTF_8),
                    null, new String[][]{{"Depth", "1"}});
            if (resp.statusCode != 200 && resp.statusCode != 207) {
                throw new IllegalStateException("PROPFIND 失败：HTTP " + resp.statusCode);
            }
            List<BackupFile> all = parseListResponse(resp.bodyText());
            List<BackupFile> zips = new ArrayList<>();
            for (BackupFile f : all) {
                if (f.name.toLowerCase(Locale.ROOT).endsWith(".zip")) zips.add(f);
            }
            return zips;
        }

        void upload(String remoteName, File local) throws Exception {
            byte[] body;
            try (InputStream in = new FileInputStream(local)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
                body = bos.toByteArray();
            }
            HttpResponse resp = rawRequest("PUT", rootUrl + remoteName, body, "application/zip", null);
            if (resp.statusCode < 200 || resp.statusCode > 299) {
                throw new IllegalStateException("PUT 失败：HTTP " + resp.statusCode);
            }
        }

        void download(String remoteName, File target) throws Exception {
            HttpResponse resp = rawRequest("GET", rootUrl + remoteName, null, null, null);
            if (resp.statusCode < 200 || resp.statusCode > 299) {
                throw new IllegalStateException("GET 失败：HTTP " + resp.statusCode);
            }
            File parent = target.getParentFile();
            if (parent != null) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            try (OutputStream out = new FileOutputStream(target)) {
                out.write(resp.body);
            }
        }

        private static class HttpResponse {
            final int statusCode;
            final byte[] body;
            HttpResponse(int statusCode, byte[] body) {
                this.statusCode = statusCode;
                this.body = body;
            }
            String bodyText() {
                return new String(body, StandardCharsets.UTF_8);
            }
        }

        private HttpResponse rawRequest(String method, String url, byte[] body,
                                        String contentType, String[][] extraHeaders) throws Exception {
            URI uri = new URI(url);
            boolean isTls = "https".equalsIgnoreCase(uri.getScheme());
            String host = uri.getHost();
            if (host == null) throw new IllegalArgumentException("URL 缺少主机名：" + url);
            int defaultPort = isTls ? 443 : 80;
            int port = uri.getPort() > 0 ? uri.getPort() : defaultPort;
            String path = uri.getRawQuery() != null ? uri.getRawPath() + "?" + uri.getRawQuery() : uri.getRawPath();
            Socket socket = isTls
                    ? SSLSocketFactory.getDefault().createSocket(host, port)
                    : new Socket(host, port);
            socket.setSoTimeout(socketTimeout);
            try {
                OutputStream out = socket.getOutputStream();
                out.write((method + " " + path + " HTTP/1.1\r\n").getBytes(StandardCharsets.US_ASCII));
                writeHeader(out, "Host", port == defaultPort ? host : host + ":" + port);
                writeHeader(out, "Authorization", authHeader);
                writeHeader(out, "Connection", "close");
                if (extraHeaders != null) {
                    for (String[] h : extraHeaders) writeHeader(out, h[0], h[1]);
                }
                if (body != null) {
                    writeHeader(out, "Content-Length", String.valueOf(body.length));
                    writeHeader(out, "Content-Type", contentType != null ? contentType : "text/xml; charset=utf-8");
                }
                out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                if (body != null) {
                    out.write(body);
                    out.flush();
                }
                InputStream in = socket.getInputStream();
                int statusCode = parseStatusCode(readLine(in));
                while (true) {
                    String line = readLine(in);
                    if (line.isEmpty()) break;
                }
                return new HttpResponse(statusCode, readUntilClose(in));
            } finally {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }

        private void writeHeader(OutputStream out, String name, String value) throws Exception {
            out.write((name + ": " + value + "\r\n").getBytes(StandardCharsets.US_ASCII));
        }

        private String readLine(InputStream in) throws Exception {
            StringBuilder sb = new StringBuilder();
            int prev = -1;
            while (true) {
                int b = in.read();
                if (b == -1) break;
                if (b == '\n' && prev == '\r') {
                    sb.setLength(sb.length() - 1);
                    break;
                }
                sb.append((char) b);
                prev = b;
            }
            return sb.toString();
        }

        private int parseStatusCode(String statusLine) {
            String[] parts = statusLine.split(" ", 3);
            if (parts.length < 2) return 0;
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private byte[] readUntilClose(InputStream in) throws Exception {
            ByteArrayOutputStream buf = new ByteArrayOutputStream(8192);
            byte[] tmp = new byte[8192];
            while (true) {
                int n = in.read(tmp);
                if (n == -1) break;
                buf.write(tmp, 0, n);
            }
            return buf.toByteArray();
        }

        private List<BackupFile> parseListResponse(String xml) throws Exception {
            List<BackupFile> result = new ArrayList<>();
            if (xml == null || xml.trim().isEmpty()) return result;
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new java.io.StringReader(xml));
            String currentHref = null;
            long currentLastModified = 0L;
            boolean currentIsDir = false;
            int ev = parser.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String name = parser.getName().toLowerCase(Locale.ROOT);
                    if ("href".equals(name)) {
                        currentHref = parser.nextText();
                    } else if ("getlastmodified".equals(name)) {
                        currentLastModified = parseHttpDate(parser.nextText());
                    } else if ("collection".equals(name)) {
                        currentIsDir = true;
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    if ("response".equals(parser.getName().toLowerCase(Locale.ROOT))) {
                        if (currentHref != null && !currentHref.isEmpty() && !currentIsDir) {
                            String name = extractFileName(currentHref);
                            if (!name.isEmpty()) result.add(new BackupFile(name, currentLastModified));
                        }
                        currentHref = null;
                        currentLastModified = 0L;
                        currentIsDir = false;
                    }
                }
                ev = parser.next();
            }
            return result;
        }

        private String extractFileName(String href) throws Exception {
            String decoded = URLDecoder.decode(href, StandardCharsets.UTF_8.name());
            while (decoded.endsWith("/")) decoded = decoded.substring(0, decoded.length() - 1);
            int idx = decoded.lastIndexOf('/');
            return idx >= 0 ? decoded.substring(idx + 1) : decoded;
        }

        private long parseHttpDate(String raw) {
            if (raw == null || raw.trim().isEmpty()) return 0L;
            try {
                return ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli();
            } catch (Exception e) {
                return 0L;
            }
        }
    }
}
