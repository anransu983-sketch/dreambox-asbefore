package com.suanran.pack.nodeforge;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.suanran.dreambox.core.pack.PackBridgeDispatcher;
import com.suanran.dreambox.core.pack.PackCallback;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * 一键节点功能包入口（v5）：实现宿主 PackBridgeDispatcher。
 *
 * 部署模型（v2 定案）：服务端脱离运行——deploy 把 deploy.sh/params.env/run.sh
 * 投递到服务器 /tmp/nodeforge/<runId>/ 后 setsid+nohup 脱离启动，全程写
 * run.log、退出码写 done；deployState 每次开短连接取快照。断网不影响部署本身。
 *
 * v5 按外部评审（2026-11-02）重构状态与 SSH 细节，修 v4 的「页面冻结」：
 * 1) 宿主每次打开页面都新建 DexClassLoader，静态字段并不跨页面存活——
 *    v5 起 state.json 是唯一状态源：每次桥调用都从落盘读、变更即落盘，
 *    deploy 在投递开始前就把表单与阶段行落盘，投递每完成一步追加落盘，
 *    页面/进程重建后按落盘状态无缝续上（launching 阶段不抢跑 SSH）。
 * 2) 每轮部署分配 runId、远端独立子目录，旧日志不会污染新一轮；
 *    存活探测用 pgrep '[n]odeforge/<runId>/run.sh' 防匹配到自己。
 * 3) JSch 通道改「先 setOutputStream/setErrStream 再 connect」的标准姿势
 *    （v4 在 connect 后才取流，开头输出与 stderr 会丢）；disconnect 改
 *    异步执行（它内部可能无界阻塞）。
 * 4) 主机密钥 TOFU：首次连接记录指纹进 state.json，之后每次比对，
 *    不一致直接报错停手；部署终结后抹掉落盘的 root 密码。
 * 5) 新增 importSubscription：把订阅链接以 ?format=clash 经宿主现成的
 *    clash://install-config 深链导入梦盒（一键建订阅配置）。
 */
public class NodeForgePackEntry implements PackBridgeDispatcher {

    private static final String BASE_DIR = "/tmp/nodeforge";
    private static final String STATE_FILE = "nodeforge-state.json";
    /** launching 阶段超过此时长视为投递线程已随进程消亡，转远端核查。 */
    private static final long LAUNCH_STALE_MS = 240_000;

    private static Context appContext;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nodeforge-io");
        t.setDaemon(true);
        return t;
    });

    // ── 落盘状态：唯一状态源 ───────────────────────────────────

    private static final class St {
        String phase = "idle"; // idle | launching | running | done | failed
        String runId = "";
        String host = "";
        int sshPort = 22;
        String sshUser = "root";
        String sshPassword = "";
        String nodeName = "";
        int nodePort = 443;
        String subId = "node1";
        String adminUser = "sui";
        String adminPass = "";
        String quotaGb = "";
        String expireDate = "";
        String protocols = "reality";
        String engine = "sui";
        String banner = "";
        String lastLog = "";
        String error = "";
        String resultJson = "";
        String hostKey = "";
        String hostKeyFp = "";
        long launchedAt = 0;

        String dir() {
            return BASE_DIR + "/" + runId;
        }
    }

    private St loadState() {
        St st = new St();
        try {
            File f = stateFile();
            if (f == null || !f.isFile()) {
                return st;
            }
            JSONObject o = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            st.phase = o.optString("phase", "idle");
            st.runId = o.optString("runId", "");
            st.host = o.optString("host", "");
            st.sshPort = o.optInt("sshPort", 22);
            st.sshUser = o.optString("sshUser", "root");
            st.sshPassword = o.optString("sshPassword", "");
            st.nodeName = o.optString("nodeName", "");
            st.nodePort = o.optInt("nodePort", 443);
            st.subId = o.optString("subId", "node1");
            st.adminUser = o.optString("adminUser", "sui");
            st.adminPass = o.optString("adminPass", "");
            st.quotaGb = o.optString("quotaGb", "");
            st.expireDate = o.optString("expireDate", "");
            st.protocols = o.optString("protocols", "reality");
            st.engine = o.optString("engine", "sui");
            st.banner = o.optString("banner", "");
            st.lastLog = o.optString("lastLog", "");
            st.error = o.optString("error", "");
            st.resultJson = o.optString("resultJson", "");
            st.hostKey = o.optString("hostKey", "");
            st.hostKeyFp = o.optString("hostKeyFp", "");
            st.launchedAt = o.optLong("launchedAt", 0);
        } catch (Exception ignored) {
        }
        return st;
    }

    private void saveState(St st) {
        try {
            File f = stateFile();
            if (f == null) {
                return;
            }
            JSONObject o = new JSONObject();
            o.put("phase", st.phase);
            o.put("runId", st.runId);
            o.put("host", st.host);
            o.put("sshPort", st.sshPort);
            o.put("sshUser", st.sshUser);
            o.put("sshPassword", st.sshPassword);
            o.put("nodeName", st.nodeName);
            o.put("nodePort", st.nodePort);
            o.put("subId", st.subId);
            o.put("adminUser", st.adminUser);
            o.put("adminPass", st.adminPass);
            o.put("quotaGb", st.quotaGb);
            o.put("expireDate", st.expireDate);
            o.put("protocols", st.protocols);
            o.put("engine", st.engine);
            o.put("banner", st.banner);
            o.put("lastLog", st.lastLog);
            o.put("error", st.error);
            o.put("resultJson", st.resultJson);
            o.put("hostKey", st.hostKey);
            o.put("hostKeyFp", st.hostKeyFp);
            o.put("launchedAt", st.launchedAt);
            Files.write(f.toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private File stateFile() {
        if (appContext == null) {
            return null;
        }
        return new File(appContext.getFilesDir(), "packs/nodeforge/" + STATE_FILE);
    }

    private static String stamp() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }

    private void stage(St st, String text) {
        st.banner += "== [" + stamp() + "] " + text + "\n";
        saveState(st);
    }

    // ── 桥入口 ─────────────────────────────────────────────────

    @Override
    public void init(Context context, Function3<? super String, ? super String, ? super String, Unit> emit) {
        appContext = context.getApplicationContext();
    }

    @Override
    public boolean dispatch(String pluginId, String method, String optionsJson, PackCallback callback) {
        if (!"NodeForge".equals(pluginId)) {
            return false;
        }
        try {
            switch (method == null ? "" : method) {
                case "deploy":
                    startDeploy(new JSONObject(optionsJson == null ? "{}" : optionsJson), callback);
                    return true;
                case "deployState":
                    callback.onResult(true, pollState().toString(), null);
                    return true;
                case "saveTextFile":
                    saveTextFile(new JSONObject(optionsJson == null ? "{}" : optionsJson), callback);
                    return true;
                case "importSubscription":
                    importSubscription(new JSONObject(optionsJson == null ? "{}" : optionsJson), callback);
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            callback.onResult(false, null, e.getMessage());
            return true;
        }
    }

    // ── importSubscription：经 clash:// 深链把订阅导入梦盒 ──────
    // s-ui 订阅加 ?format=clash 返回 Clash YAML（按查询参数切换、不看 UA）；
    // 深链由宿主 MainActivity 的 install-config 处理接住。不用 location.href
    // 跳（WebView 未处理 shouldOverrideUrlLoading 会报未知协议），包内直接发
    // Intent 并 setPackage 锁定本应用。

    private void importSubscription(JSONObject opts, PackCallback callback) {
        try {
            String url = opts.getString("url").trim();
            if (url.isEmpty()) {
                throw new IllegalArgumentException("订阅链接为空");
            }
            String clashUrl = url + (url.contains("?") ? "&" : "?") + "format=clash";
            String deep = "clash://install-config?url="
                    + URLEncoder.encode(clashUrl, "UTF-8");
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(deep));
            intent.setPackage(appContext.getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(intent);
            JSONObject r = new JSONObject();
            r.put("ok", true);
            callback.onResult(true, r.toString(), null);
        } catch (Exception e) {
            callback.onResult(false, null, "打开导入失败：" + e.getMessage());
        }
    }

    // ── saveTextFile：把文本存进系统 Download 目录 ──────────────
    // WebView 里的 Blob+a[download] 没有宿主 DownloadListener 接管、点了没反应，
    // 故走原生：API 29+ 用 MediaStore 写公共 Download（免权限），低版本退回应用外部目录。

    private void saveTextFile(JSONObject opts, PackCallback callback) {
        try {
            String name = opts.optString("name", "nodeforge.yaml");
            if (name.isBlank() || name.contains("/") || name.contains("..")) {
                throw new IllegalArgumentException("文件名不合法");
            }
            byte[] bytes = opts.optString("text", "").getBytes(StandardCharsets.UTF_8);
            String where;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver cr = appContext.getContentResolver();
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, "text/yaml");
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) {
                    throw new IllegalStateException("系统拒绝创建下载文件");
                }
                try (OutputStream out = cr.openOutputStream(uri)) {
                    if (out == null) {
                        throw new IllegalStateException("无法打开下载文件");
                    }
                    out.write(bytes);
                }
                cv.clear();
                cv.put(MediaStore.Downloads.IS_PENDING, 0);
                cr.update(uri, cv, null, null);
                where = "Download/" + name;
            } else {
                File dir = new File(appContext.getExternalFilesDir(null), "Download");
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new IllegalStateException("无法创建保存目录");
                }
                File f = new File(dir, name);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(bytes);
                }
                where = f.getAbsolutePath();
            }
            JSONObject r = new JSONObject();
            r.put("ok", true);
            r.put("path", where);
            callback.onResult(true, r.toString(), null);
        } catch (Exception e) {
            callback.onResult(false, null, "保存失败：" + e.getMessage());
        }
    }

    // ── deploy：先落盘，再后台投递 ─────────────────────────────

    private void startDeploy(JSONObject opts, PackCallback callback) {
        try {
            St cur = loadState();
            if ("launching".equals(cur.phase)
                    && System.currentTimeMillis() - cur.launchedAt < LAUNCH_STALE_MS) {
                callback.onResult(false, null, "正在投递上一个部署，请稍等");
                return;
            }
            if ("running".equals(cur.phase)) {
                callback.onResult(false, null, "已有一轮部署在服务器上运行中，先等它跑完");
                return;
            }
            final St st = new St();
            st.phase = "launching";
            st.runId = Long.toString(System.currentTimeMillis(), 36);
            st.host = opts.getString("host").trim();
            st.sshPort = opts.optInt("sshPort", 22);
            st.sshUser = opts.optString("sshUser", "root");
            st.sshPassword = opts.getString("sshPassword");
            st.nodeName = opts.optString("nodeName", "");
            st.nodePort = opts.optInt("nodePort", 443);
            st.subId = opts.optString("subId", "node1");
            st.adminUser = opts.optString("adminUser", "sui");
            st.adminPass = opts.optString("adminPass", "");
            st.quotaGb = opts.optString("quotaGb", "");
            st.expireDate = opts.optString("expireDate", "");
            st.protocols = opts.optString("protocols", "reality");
            st.engine = opts.optString("engine", "sui");
            st.launchedAt = System.currentTimeMillis();
            // 同一台机器沿用已记录的指纹做 TOFU 比对；换机器则重新记录。
            if (cur.host.equals(st.host) && cur.sshPort == st.sshPort) {
                st.hostKey = cur.hostKey;
                st.hostKeyFp = cur.hostKeyFp;
            }
            st.banner = "== [" + stamp() + "] 准备投递到 "
                    + st.sshUser + "@" + maskHost(st.host) + ":" + st.sshPort + " …\n";
            saveState(st);
            callback.onResult(true, "{\"started\":true}", null);

            executor.execute(() -> deliver(st));
        } catch (Exception e) {
            callback.onResult(false, null,
                    e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    private void deliver(St st) {
        Session session = null;
        try {
            boolean sbEngine = "singbox".equals(st.engine);
            File script = new File(appContext.getFilesDir(),
                    "packs/nodeforge/" + (sbEngine ? "deploy-singbox.sh" : "deploy.sh"));
            if (!script.isFile()) {
                throw new IllegalStateException("功能包内缺少部署脚本，包可能损坏，请删包重装");
            }
            byte[] scriptBytes = Files.readAllBytes(script.toPath());
            String dir = st.dir();

            stage(st, "连接服务器 …");
            session = openSessionChecked(st);
            if (!st.hostKeyFp.isEmpty()) {
                stage(st, "服务器指纹 " + st.hostKeyFp);
            }
            stage(st, "创建远端目录 " + dir);
            execOrThrow(session, "mkdir -p " + dir + " && chmod 700 " + dir, null, 15_000);

            String params = "NF_ADMIN_USER=" + shq(st.adminUser) + "\n"
                    + "NF_ADMIN_PASS=" + shq(st.adminPass) + "\n"
                    + "NF_SUB_ID=" + shq(st.subId) + "\n"
                    + "NF_NODE_PORT=" + st.nodePort + "\n"
                    + "NF_SSH_PORT=" + st.sshPort + "\n"
                    + "NF_PUBLIC_HOST=" + shq(st.host) + "\n"
                    + "NF_NODE_NAME=" + shq(st.nodeName) + "\n"
                    + "NF_QUOTA_GB=" + shq(st.quotaGb) + "\n"
                    + "NF_EXPIRE_DATE=" + shq(st.expireDate) + "\n"
                    + "NF_PROTOCOLS=" + shq(st.protocols) + "\n";
            String wrapper = "#!/bin/bash\n"
                    + "set -a\n"
                    + ". " + dir + "/params.env\n"
                    + "set +a\n"
                    + "rm -f " + dir + "/params.env\n"
                    + "bash " + dir + "/deploy.sh\n"
                    + "echo $? > " + dir + "/done.tmp\n"
                    + "mv " + dir + "/done.tmp " + dir + "/done\n";
            stage(st, "投递部署脚本 …");
            execOrThrow(session, "cat > " + dir + "/deploy.sh && chmod 700 " + dir + "/deploy.sh",
                    scriptBytes, 25_000);
            File extFile = new File(appContext.getFilesDir(), "packs/nodeforge/sub-ext.yaml");
            if (extFile.isFile()) {
                execOrThrow(session, "cat > " + dir + "/sub-ext.yaml && chmod 600 " + dir + "/sub-ext.yaml",
                        Files.readAllBytes(extFile.toPath()), 15_000);
            }
            stage(st, "投递部署参数 …");
            execOrThrow(session, "cat > " + dir + "/params.env && chmod 600 " + dir + "/params.env",
                    params.getBytes(StandardCharsets.UTF_8), 15_000);
            execOrThrow(session, "cat > " + dir + "/run.sh && chmod 700 " + dir + "/run.sh",
                    wrapper.getBytes(StandardCharsets.UTF_8), 15_000);
            stage(st, "脱离启动（断网/切后台都不会中断）…");
            execOrThrow(session,
                    "rm -f " + dir + "/done " + dir + "/run.log; "
                            + "setsid nohup bash " + dir + "/run.sh > " + dir + "/run.log 2>&1 < /dev/null & "
                            + "sleep 1; pgrep -f '[n]odeforge/" + st.runId + "/run.sh' > /dev/null",
                    null, 15_000);
            st.phase = "running";
            stage(st, "部署已在服务器后台运行");
        } catch (Exception e) {
            st.phase = "failed";
            st.error = "投递失败：" + (e.getMessage() != null ? e.getMessage() : e.toString());
            scrubPassword(st);
            saveState(st);
        } finally {
            if (session != null) {
                disconnectQuietly(session);
            }
        }
    }

    // ── deployState：按落盘阶段取状态 ──────────────────────────

    private synchronized JSONObject pollState() throws Exception {
        St st = loadState();
        JSONObject o = new JSONObject();
        o.put("form", formJson(st));
        switch (st.phase) {
            case "launching": {
                long age = System.currentTimeMillis() - st.launchedAt;
                if (age < LAUNCH_STALE_MS) {
                    // 投递线程还活着（或刚随页面重建）：只报进度，不抢跑 SSH。
                    o.put("running", true);
                    o.put("launching", true);
                    o.put("done", false);
                    o.put("ok", false);
                    o.put("log", st.banner);
                    return o;
                }
                // 投递线程多半已随进程消亡：核查远端是否其实已启动。
                if (!snapshotFromRemote(st)) {
                    st.phase = "failed";
                    st.error = "投递中断（页面或进程被关闭），服务器上没有这一轮部署，请重新部署";
                    scrubPassword(st);
                    saveState(st);
                    return terminalJson(o, st);
                }
                return runningJson(o, st);
            }
            case "running": {
                boolean linkDown = false;
                try {
                    if (!snapshotFromRemote(st)) {
                        linkDown = true;
                    }
                } catch (Exception e) {
                    linkDown = true;
                }
                if (linkDown) {
                    o.put("linkDown", true);
                    return runningJson(o, st);
                }
                if ("done".equals(st.phase) || "failed".equals(st.phase)) {
                    return terminalJson(o, st);
                }
                return runningJson(o, st);
            }
            case "done":
            case "failed":
                return terminalJson(o, st);
            default: // idle
                o.put("running", false);
                o.put("done", false);
                o.put("ok", false);
                o.put("log", "");
                return o;
        }
    }

    private JSONObject runningJson(JSONObject o, St st) throws Exception {
        o.put("running", true);
        o.put("done", false);
        o.put("ok", false);
        o.put("log", st.banner + st.lastLog);
        return o;
    }

    private JSONObject terminalJson(JSONObject o, St st) throws Exception {
        o.put("running", false);
        o.put("done", true);
        o.put("ok", "done".equals(st.phase));
        o.put("log", st.banner + st.lastLog);
        if (!st.error.isEmpty()) {
            o.put("error", st.error);
        }
        if (!st.resultJson.isEmpty()) {
            o.put("result", new JSONObject(st.resultJson));
        }
        return o;
    }

    private JSONObject formJson(St st) throws Exception {
        JSONObject f = new JSONObject();
        f.put("host", st.host);
        f.put("sshPort", st.sshPort);
        f.put("sshUser", st.sshUser);
        f.put("sshPassword", st.sshPassword);
        f.put("nodeName", st.nodeName);
        f.put("nodePort", st.nodePort);
        f.put("subId", st.subId);
        f.put("adminUser", st.adminUser);
        f.put("adminPass", st.adminPass);
        f.put("protocols", st.protocols);
        f.put("engine", st.engine);
        return f;
    }

    /** 开短连接取一轮快照并解析；成功取到（哪怕部署未完）返回 true，网络失败返回 false。 */
    private boolean snapshotFromRemote(St st) {
        Session session = null;
        try {
            session = openSessionChecked(st);
            String dir = st.dir();
            String snap = execOrThrow(session,
                    "echo ---LOG---; cat " + dir + "/run.log 2>/dev/null; "
                            + "echo ---DONE---; cat " + dir + "/done 2>/dev/null; "
                            + "echo ---RUN---; pgrep -f '[n]odeforge/" + st.runId + "/run.sh' > /dev/null && echo yes || echo no",
                    null, 15_000);
            parseSnapshot(st, snap);
            saveState(st);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (session != null) {
                disconnectQuietly(session);
            }
        }
    }

    private void parseSnapshot(St st, String snap) {
        String log = section(snap, "---LOG---", "---DONE---");
        String doneText = section(snap, "---DONE---", "---RUN---").trim();
        String runText = section(snap, "---RUN---", null).trim();
        if (log != null) {
            st.lastLog = log;
        }
        String resultJson = null;
        String errorLine = null;
        for (String line : st.lastLog.split("\n")) {
            if (line.startsWith("NF_RESULT ")) {
                resultJson = line.substring("NF_RESULT ".length()).trim();
            } else if (line.startsWith("NF_ERROR:")) {
                errorLine = line.substring("NF_ERROR:".length()).trim();
            }
        }
        boolean alive = "yes".equals(runText);
        if (!doneText.isEmpty()) {
            int exit = -1;
            try {
                exit = Integer.parseInt(doneText.split("\\s+")[0]);
            } catch (Exception ignored) {
            }
            if (exit == 0 && resultJson != null) {
                st.phase = "done";
                st.error = "";
                st.resultJson = resultJson;
                // 表单端口自校正：服务端沿用已有入站端口时，把结果端口回填表单，
                // 免得历史误填的端口（如 8451）被存下来反复带进下次部署。
                try {
                    int np = new JSONObject(resultJson).optInt("nodePort", 0);
                    if (np > 0) {
                        st.nodePort = np;
                    }
                } catch (Exception ignored) {
                }
            } else {
                st.phase = "failed";
                st.error = errorLine != null ? errorLine
                        : "远端脚本异常退出（exit=" + exit + "），细节看日志末尾";
            }
            scrubPassword(st);
        } else if (!alive && "running".equals(st.phase)) {
            st.phase = "failed";
            st.error = errorLine != null ? errorLine
                    : "部署进程意外结束（未生成结果），细节看日志末尾";
            scrubPassword(st);
        } else if (alive && "launching".equals(st.phase)) {
            st.phase = "running";
        }
    }

    private void scrubPassword(St st) {
        st.sshPassword = "";
    }

    private static String section(String text, String startMark, String endMark) {
        int s = text.indexOf(startMark);
        if (s < 0) return "";
        s += startMark.length();
        if (s < text.length() && text.charAt(s) == '\n') s++;
        int e = endMark == null ? text.length() : text.indexOf(endMark, s);
        if (e < 0) e = text.length();
        return text.substring(s, e);
    }

    // ── SSH 基础 ───────────────────────────────────────────────

    /** 连接并做 TOFU 指纹比对：state 里有记录且不一致则停手；首次则记录（由调用方落盘时机决定）。 */
    private Session openSessionChecked(St st) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(st.sshUser, st.host, st.sshPort);
        session.setPassword(st.sshPassword);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(20_000);
        session.setServerAliveInterval(10_000);
        session.setServerAliveCountMax(3);
        session.connect(15_000);
        try {
            if (session.getHostKey() != null) {
                String key = session.getHostKey().getKey();
                String fp = session.getHostKey().getFingerPrint(jsch);
                if (!st.hostKey.isEmpty() && !st.hostKey.equals(key)) {
                    throw new IllegalStateException(
                            "服务器主机指纹与首次记录不一致（记录 " + st.hostKeyFp
                                    + "，当前 " + fp + "），已停手：确认服务器没被重装/替换后再重新部署");
                }
                if (st.hostKey.isEmpty()) {
                    st.hostKey = key;
                    st.hostKeyFp = fp;
                }
            }
        } catch (Exception e) {
            disconnectQuietly(session);
            throw e;
        }
        return session;
    }

    /** JSch 的 disconnect 内部可能无界阻塞，丢到守护线程里不等它。 */
    private static void disconnectQuietly(Session session) {
        Thread t = new Thread(session::disconnect, "nodeforge-disconnect");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 执行一条命令，可选 stdin 载荷（写完即 EOF），返回 stdout+stderr 合文，带硬超时。
     * 流必须在 connect 之前设置（JSch 标准姿势）：connect 后再取流会丢开头输出，
     * stderr 尤其容易整段丢失。
     */
    private String execOrThrow(Session session, String command, byte[] stdinBytes, int timeoutMs)
            throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(new ByteArrayInputStream(
                stdinBytes == null ? new byte[0] : stdinBytes));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        channel.setOutputStream(out);
        channel.setErrStream(out);
        channel.connect(10_000);
        try {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (!channel.isClosed()) {
                if (System.currentTimeMillis() > deadline) {
                    throw new IllegalStateException("远端命令超时：" + command);
                }
                Thread.sleep(100);
            }
            // 关闭后再等一拍，确保泵线程把尾部输出刷完。
            Thread.sleep(60);
            int exit = channel.getExitStatus();
            String text = out.toString("UTF-8");
            if (exit != 0) {
                throw new IllegalStateException(
                        "远端命令失败（exit=" + exit + "）：" + command + " → " + text.trim());
            }
            return text;
        } finally {
            channel.disconnect();
        }
    }

    /** 展示打码：IPv4 只留首尾段（216.*.*.4），域名留首尾标签。 */
    private static String maskHost(String h) {
        if (h == null || h.isEmpty()) return "***";
        String[] p = h.split("\\.");
        if (p.length == 4) {
            boolean num = true;
            for (String s : p) {
                if (!s.matches("\\d+")) { num = false; break; }
            }
            if (num) return p[0] + ".*.*." + p[3];
        }
        if (p.length >= 2) return p[0] + ".***." + p[p.length - 1];
        return "***";
    }

    /** shell 单引号安全引用（密码可含任意字符）。 */
    private static String shq(String v) {
        return "'" + v.replace("'", "'\\''") + "'";
    }

    @Override
    public void release() {
        // 状态全在落盘 state.json，页面销毁不清；执行器为守护线程，随类加载器回收。
    }
}
