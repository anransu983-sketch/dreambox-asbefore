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

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * 一键节点功能包入口（v2）：实现宿主 PackBridgeDispatcher。
 *
 * v1 教训（2026-11-02 真机实测）：单条 SSH 通道挂着跑完整个脚本，
 * apt 静默期里手机网络把连接掐断，脚本被 SIGPIPE 打死、页面永远「运行中」。
 * v2 改为服务端脱离运行：
 * - deploy：短连接把 deploy.sh / params.env / run.sh 写到服务器 /tmp/nodeforge/，
 *   再用 setsid+nohup 脱离启动，部署全程写 run.log，结果码写 done 文件，随即断开。
 *   之后手机断网、切后台、甚至重启 App 都不影响部署本身。
 * - deployState：每次开一条短连接取 run.log + done + 进程存活快照解析状态；
 *   取不到（网络抖动）就回上次快照并标记 linkDown，绝不假死。
 * - 页面重开时 deployState 也能凭内存里的目标信息重新接上正在跑的部署。
 *
 * root 密码与面板密码只在内存与 params.env（启动后 run.sh 立即删除）中出现，
 * 不进 run.log（脚本输出不含密码）。
 */
public class NodeForgePackEntry implements PackBridgeDispatcher {

    private static final String DIR = "/tmp/nodeforge";

    private Context appContext;

    /** 当前部署目标（仅内存，供 deployState 短连接轮询）。 */
    private static final class Target {
        String host;
        int sshPort;
        String sshUser;
        String sshPassword;
    }

    private volatile Target target;
    private volatile String banner = "";
    private volatile String lastLog = "";
    private volatile boolean started;      // 本进程内已成功投递过一次部署
    private volatile boolean done;
    private volatile boolean ok;
    private volatile String error;
    private volatile JSONObject result;

    @Override
    public void init(Context context, Function3<? super String, ? super String, ? super String, Unit> emit) {
        this.appContext = context.getApplicationContext();
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

    // ── deploy：投递脚本并脱离启动 ─────────────────────────────

    private void startDeploy(JSONObject opts, PackCallback callback) {
        try {
            Target t = new Target();
            t.host = opts.getString("host").trim();
            t.sshPort = opts.optInt("sshPort", 22);
            t.sshUser = opts.optString("sshUser", "root");
            t.sshPassword = opts.getString("sshPassword");
            String adminUser = opts.getString("adminUser");
            String adminPass = opts.getString("adminPass");
            String subId = opts.optString("subId", "node1");
            int nodePort = opts.optInt("nodePort", 443);

            File script = new File(appContext.getFilesDir(), "packs/nodeforge/deploy.sh");
            if (!script.isFile()) {
                throw new IllegalStateException("功能包内缺少 deploy.sh，包可能损坏，请删包重装");
            }
            byte[] scriptBytes = Files.readAllBytes(script.toPath());

            StringBuilder b = new StringBuilder();
            b.append("== 连接 ").append(t.sshUser).append("@").append(t.host)
                    .append(":").append(t.sshPort).append(" …\n");

            JSch jsch = new JSch();
            Session session = openSession(jsch, t);
            try {
                if (session.getHostKey() != null) {
                    b.append("== 服务器指纹 ")
                            .append(session.getHostKey().getFingerPrint(jsch)).append("\n");
                }
                execOrThrow(session, "mkdir -p " + DIR + " && chmod 700 " + DIR, null, 15_000);

                String params = "NF_ADMIN_USER=" + shq(adminUser) + "\n"
                        + "NF_ADMIN_PASS=" + shq(adminPass) + "\n"
                        + "NF_SUB_ID=" + shq(subId) + "\n"
                        + "NF_NODE_PORT=" + nodePort + "\n"
                        + "NF_SSH_PORT=" + t.sshPort + "\n";
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

            b.append("== 部署已在服务器后台启动（断网/切后台都不会中断）\n");
            this.target = t;
            this.banner = b.toString();
            this.lastLog = "";
            this.started = true;
            this.done = false;
            this.ok = false;
            this.error = null;
            this.result = null;
            callback.onResult(true, "{\"started\":true}", null);
        } catch (Exception e) {
            callback.onResult(false, null,
                    e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    // ── deployState：短连接取快照 ──────────────────────────────

    private synchronized JSONObject pollState() throws Exception {
        JSONObject o = new JSONObject();
        Target t = this.target;
        if (t == null || !started) {
            o.put("running", false);
            o.put("done", false);
            o.put("ok", false);
            o.put("log", "");
            return o;
        }
        boolean linkDown = false;
        try {
            JSch jsch = new JSch();
            Session session = openSession(jsch, t);
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
        o.put("running", started && !done);
        o.put("done", done);
        o.put("ok", ok);
        o.put("log", banner + lastLog);
        if (linkDown && !done) {
            o.put("linkDown", true);
        }
        if (error != null) {
            o.put("error", error);
        }
        if (result != null) {
            o.put("result", result);
        }
        return o;
    }

    private void parseSnapshot(String snap) {
        String log = section(snap, "---LOG---", "---DONE---");
        String doneText = section(snap, "---DONE---", "---RUN---").trim();
        String runText = section(snap, "---RUN---", null).trim();
        if (log != null) {
            lastLog = log;
        }
        String resultJson = null;
        String errorLine = null;
        for (String line : lastLog.split("\n")) {
            if (line.startsWith("NF_RESULT ")) {
                resultJson = line.substring("NF_RESULT ".length()).trim();
            } else if (line.startsWith("NF_ERROR:")) {
                errorLine = line.substring("NF_ERROR:".length()).trim();
            }
        }
        boolean alive = "yes".equals(runText);
        if (!doneText.isEmpty()) {
            done = true;
            int exit = -1;
            try {
                exit = Integer.parseInt(doneText.split("\\s+")[0]);
            } catch (Exception ignored) {
            }
            if (exit == 0 && resultJson != null) {
                ok = true;
                error = null;
                try {
                    result = new JSONObject(resultJson);
                } catch (Exception ignored) {
                }
            } else {
                ok = false;
                error = errorLine != null ? errorLine
                        : "远端脚本异常退出（exit=" + exit + "），细节看日志末尾";
            }
        } else if (!alive) {
            // 进程不在、又没有 done 文件：被系统清理或异常终止
            done = true;
            ok = false;
            error = errorLine != null ? errorLine
                    : "部署进程意外结束（未生成结果），细节看日志末尾";
        }
    }

    private static String section(String text, String startMark, String endMark) {
        int s = text.indexOf(startMark);
        if (s < 0) return "";
        s += startMark.length();
        if (text.charAt(s) == '\n') s++;
        int e = endMark == null ? text.length() : text.indexOf(endMark, s);
        if (e < 0) e = text.length();
        return text.substring(s, e);
    }

    // ── SSH 基础 ───────────────────────────────────────────────

    private Session openSession(JSch jsch, Target t) throws Exception {
        Session session = jsch.getSession(t.sshUser, t.host, t.sshPort);
        session.setPassword(t.sshPassword);
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
        // 无常驻线程：轮询都是短连接，随页面生灭即可。
    }
}
