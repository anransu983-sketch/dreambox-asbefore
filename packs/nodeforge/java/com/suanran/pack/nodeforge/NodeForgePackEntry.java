package com.suanran.pack.nodeforge;

import android.content.Context;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.suanran.dreambox.core.pack.PackBridgeDispatcher;
import com.suanran.dreambox.core.pack.PackCallback;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * 一键节点功能包入口（v4）：实现宿主 PackBridgeDispatcher。
 *
 * 部署模型（v2 定案）：服务端脱离运行——deploy 把 deploy.sh/params.env/run.sh
 * 投递到服务器 /tmp/nodeforge/ 后 setsid+nohup 脱离启动，全程写 run.log、
 * 退出码写 done；deployState 每次开短连接取快照。断网不影响部署本身。
 *
 * v4 修「页面失忆」（v3 真机实测）：宿主 PackHostActivity 每次打开页面都
 * newInstance 一个新入口对象，v2/v3 把部署状态放在实例字段里，用户一切走
 * 再回来状态就没了、进度框空白。v4 起状态全放静态区 + 落盘 state.json
 * （含目标与口令，存包私有目录，App 沙箱内），页面/Activity/进程重建后
 * deployState 都能凭落盘目标重新接上服务器上的部署。
 * 另：deploy 的 SSH 投递改后台线程执行，点完立刻返回，日志不再空白等待。
 */
public class NodeForgePackEntry implements PackBridgeDispatcher {

    private static final String DIR = "/tmp/nodeforge";
    private static final String STATE_FILE = "nodeforge-state.json";

    private static Context appContext;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nodeforge-io");
        t.setDaemon(true);
        return t;
    });

    /** 部署状态：静态单例，跨入口实例存活。 */
    private static final class S {
        static volatile String host;
        static volatile int sshPort;
        static volatile String sshUser;
        static volatile String sshPassword;
        static volatile String banner = "";
        static volatile String lastLog = "";
        static volatile boolean started;
        static volatile boolean launching;
        static volatile boolean done;
        static volatile boolean ok;
        static volatile String error;
        static volatile JSONObject result;
    }

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
                default:
                    return false;
            }
        } catch (Exception e) {
            callback.onResult(false, null, e.getMessage());
            return true;
        }
    }

    // ── deploy：后台线程投递脚本并脱离启动 ────────────────────

    private void startDeploy(JSONObject opts, PackCallback callback) {
        try {
            final String host = opts.getString("host").trim();
            final int sshPort = opts.optInt("sshPort", 22);
            final String sshUser = opts.optString("sshUser", "root");
            final String sshPassword = opts.getString("sshPassword");
            final String adminUser = opts.getString("adminUser");
            final String adminPass = opts.getString("adminPass");
            final String subId = opts.optString("subId", "node1");
            final int nodePort = opts.optInt("nodePort", 443);

            if (S.launching) {
                callback.onResult(false, null, "正在投递上一个部署，请稍等");
                return;
            }
            S.launching = true;
            S.started = false;
            S.done = false;
            S.ok = false;
            S.error = null;
            S.result = null;
            S.lastLog = "";
            S.banner = "== 连接 " + sshUser + "@" + host + ":" + sshPort + " …\n";
            callback.onResult(true, "{\"started\":true}", null);

            EXECUTOR.execute(() -> {
                try {
                    File script = new File(appContext.getFilesDir(), "packs/nodeforge/deploy.sh");
                    if (!script.isFile()) {
                        throw new IllegalStateException("功能包内缺少 deploy.sh，包可能损坏，请删包重装");
                    }
                    byte[] scriptBytes = Files.readAllBytes(script.toPath());

                    JSch jsch = new JSch();
                    Session session = openSession(host, sshPort, sshUser, sshPassword);
                    try {
                        if (session.getHostKey() != null) {
                            S.banner += "== 服务器指纹 "
                                    + session.getHostKey().getFingerPrint(jsch) + "\n";
                        }
                        execOrThrow(session, "mkdir -p " + DIR + " && chmod 700 " + DIR, null, 15_000);

                        String params = "NF_ADMIN_USER=" + shq(adminUser) + "\n"
                                + "NF_ADMIN_PASS=" + shq(adminPass) + "\n"
                                + "NF_SUB_ID=" + shq(subId) + "\n"
                                + "NF_NODE_PORT=" + nodePort + "\n"
                                + "NF_SSH_PORT=" + sshPort + "\n";
                        String wrapper = "#!/bin/bash\n"
                                + "set -a\n"
                                + ". " + DIR + "/params.env\n"
                                + "set +a\n"
                                + "rm -f " + DIR + "/params.env\n"
                                + "bash " + DIR + "/deploy.sh\n"
                                + "echo $? > " + DIR + "/done.tmp\n"
                                + "mv " + DIR + "/done.tmp " + DIR + "/done\n";
                        execOrThrow(session, "cat > " + DIR + "/deploy.sh && chmod 700 " + DIR + "/deploy.sh",
                                scriptBytes, 20_000);
                        execOrThrow(session, "cat > " + DIR + "/params.env && chmod 600 " + DIR + "/params.env",
                                params.getBytes(StandardCharsets.UTF_8), 15_000);
                        execOrThrow(session, "cat > " + DIR + "/run.sh && chmod 700 " + DIR + "/run.sh",
                                wrapper.getBytes(StandardCharsets.UTF_8), 15_000);
                        // 脱离启动：setsid + nohup + 输出全进 run.log，SSH 断开不影响部署。
                        execOrThrow(session,
                                "rm -f " + DIR + "/done " + DIR + "/run.log; "
                                        + "setsid nohup bash " + DIR + "/run.sh > " + DIR + "/run.log 2>&1 < /dev/null & "
                                        + "sleep 1; pgrep -f 'nodeforge/run.sh' > /dev/null",
                                null, 15_000);
                    } finally {
                        session.disconnect();
                    }

                    S.host = host;
                    S.sshPort = sshPort;
                    S.sshUser = sshUser;
                    S.sshPassword = sshPassword;
                    S.banner += "== 部署已在服务器后台启动（断网/切后台都不会中断）\n";
                    S.started = true;
                    persistState();
                } catch (Exception e) {
                    S.done = true;
                    S.ok = false;
                    S.error = "投递失败：" + (e.getMessage() != null ? e.getMessage() : e.toString());
                } finally {
                    S.launching = false;
                }
            });
        } catch (Exception e) {
            callback.onResult(false, null,
                    e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    // ── 状态落盘/恢复（页面与进程重建后重新接上） ──────────────

    private void persistState() {
        try {
            JSONObject o = new JSONObject();
            o.put("host", S.host);
            o.put("sshPort", S.sshPort);
            o.put("sshUser", S.sshUser);
            o.put("sshPassword", S.sshPassword);
            o.put("banner", S.banner);
            File f = new File(appContext.getFilesDir(), "packs/nodeforge/" + STATE_FILE);
            Files.write(f.toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private void restoreState() {
        if (S.started || appContext == null) {
            return;
        }
        try {
            File f = new File(appContext.getFilesDir(), "packs/nodeforge/" + STATE_FILE);
            if (!f.isFile()) {
                return;
            }
            JSONObject o = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            S.host = o.getString("host");
            S.sshPort = o.optInt("sshPort", 22);
            S.sshUser = o.optString("sshUser", "root");
            S.sshPassword = o.getString("sshPassword");
            S.banner = o.optString("banner", "");
            S.started = true;
        } catch (Exception ignored) {
        }
    }

    // ── deployState：短连接取快照 ──────────────────────────────

    private synchronized JSONObject pollState() throws Exception {
        restoreState();
        JSONObject o = new JSONObject();
        if (!S.started && !S.launching) {
            o.put("running", false);
            o.put("done", S.done);
            o.put("ok", false);
            o.put("log", S.banner + S.lastLog);
            if (S.error != null) {
                o.put("error", S.error);
            }
            return o;
        }
        boolean linkDown = false;
        if (S.started && !S.done) {
            try {
                JSch jsch = new JSch();
                Session session = openSession(S.host, S.sshPort, S.sshUser, S.sshPassword);
                String snap;
                try {
                    snap = execOrThrow(session,
                            "echo ---LOG---; cat " + DIR + "/run.log 2>/dev/null; "
                                    + "echo ---DONE---; cat " + DIR + "/done 2>/dev/null; "
                                    + "echo ---RUN---; pgrep -f 'nodeforge/run.sh' > /dev/null && echo yes || echo no",
                            null, 15_000);
                } finally {
                    session.disconnect();
                }
                parseSnapshot(snap);
            } catch (Exception e) {
                // 网络抖动：保留上次状态，不判死
                linkDown = true;
            }
        }
        o.put("running", S.launching || (S.started && !S.done));
        o.put("done", S.done);
        o.put("ok", S.ok);
        o.put("log", S.banner + S.lastLog);
        if (linkDown && !S.done) {
            o.put("linkDown", true);
        }
        if (S.error != null) {
            o.put("error", S.error);
        }
        if (S.result != null) {
            o.put("result", S.result);
        }
        return o;
    }

    private void parseSnapshot(String snap) {
        String log = section(snap, "---LOG---", "---DONE---");
        String doneText = section(snap, "---DONE---", "---RUN---").trim();
        String runText = section(snap, "---RUN---", null).trim();
        if (log != null) {
            S.lastLog = log;
        }
        String resultJson = null;
        String errorLine = null;
        for (String line : S.lastLog.split("\n")) {
            if (line.startsWith("NF_RESULT ")) {
                resultJson = line.substring("NF_RESULT ".length()).trim();
            } else if (line.startsWith("NF_ERROR:")) {
                errorLine = line.substring("NF_ERROR:".length()).trim();
            }
        }
        boolean alive = "yes".equals(runText);
        if (!doneText.isEmpty()) {
            S.done = true;
            int exit = -1;
            try {
                exit = Integer.parseInt(doneText.split("\\s+")[0]);
            } catch (Exception ignored) {
            }
            if (exit == 0 && resultJson != null) {
                S.ok = true;
                S.error = null;
                try {
                    S.result = new JSONObject(resultJson);
                } catch (Exception ignored) {
                }
            } else {
                S.ok = false;
                S.error = errorLine != null ? errorLine
                        : "远端脚本异常退出（exit=" + exit + "），细节看日志末尾";
            }
        } else if (!alive) {
            // 进程不在、又没有 done 文件：被系统清理或异常终止
            S.done = true;
            S.ok = false;
            S.error = errorLine != null ? errorLine
                    : "部署进程意外结束（未生成结果），细节看日志末尾";
        }
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

    private Session openSession(String host, int port, String user, String password) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(user, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(20_000);
        session.setServerAliveInterval(10_000);
        session.setServerAliveCountMax(3);
        session.connect(15_000);
        return session;
    }

    /** 执行一条命令，可选 stdin 载荷（写完即 EOF），返回 stdout+stderr 合文，带硬超时。 */
    private String execOrThrow(Session session, String command, byte[] stdinBytes, int timeoutMs)
            throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(new ByteArrayInputStream(
                stdinBytes == null ? new byte[0] : stdinBytes));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        channel.connect(10_000);
        try {
            long deadline = System.currentTimeMillis() + timeoutMs;
            InputStream in = channel.getInputStream();
            InputStream err = channel.getErrStream();
            byte[] buf = new byte[4096];
            while (true) {
                while (in.available() > 0) {
                    int n = in.read(buf, 0, Math.min(buf.length, in.available()));
                    if (n < 0) break;
                    out.write(buf, 0, n);
                }
                while (err.available() > 0) {
                    int n = err.read(buf, 0, Math.min(buf.length, err.available()));
                    if (n < 0) break;
                    out.write(buf, 0, n);
                }
                if (channel.isClosed()) {
                    if (in.available() <= 0 && err.available() <= 0) break;
                } else if (System.currentTimeMillis() > deadline) {
                    throw new IllegalStateException("远端命令超时：" + command);
                } else {
                    Thread.sleep(120);
                }
            }
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

    /** shell 单引号安全引用（密码可含任意字符）。 */
    private static String shq(String v) {
        return "'" + v.replace("'", "'\\''") + "'";
    }

    @Override
    public void release() {
        // 状态在静态区+落盘，页面销毁不清状态；执行器为进程级守护线程，不关。
    }
}
