package com.suanran.pack.nodeforge;

import android.content.Context;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.suanran.dreambox.core.pack.PackBridgeDispatcher;
import com.suanran.dreambox.core.pack.PackCallback;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * 一键节点功能包入口：实现宿主 PackBridgeDispatcher。
 *
 * 网页只调两个方法（pluginId = NodeForge）：
 * - deploy：起后台线程，JSch 连 SSH 把包内 deploy.sh 喂给远端 bash 执行；
 *   立刻返回，进度靠 deployState 轮询（不依赖事件通道，断线重进也能看状态）。
 * - deployState：返回 {running, done, ok, log, result, error}。
 *
 * root 密码与面板密码只在内存中经 JSch 与 env 传递：不落盘、不进日志
 * （部署脚本的输出里也不含密码；env 值不回显）。
 */
public class NodeForgePackEntry implements PackBridgeDispatcher {

    private Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nodeforge-deploy");
        t.setDaemon(true);
        return t;
    });
    private final Job job = new Job();

    private static final class Job {
        volatile boolean running;
        volatile boolean done;
        volatile boolean ok;
        volatile String error;
        volatile JSONObject result;
        final StringBuilder log = new StringBuilder();

        synchronized void reset() {
            running = true;
            done = false;
            ok = false;
            error = null;
            result = null;
            log.setLength(0);
        }

        synchronized void append(String s) {
            if (log.length() > 128 * 1024) {
                log.delete(0, log.length() - 96 * 1024);
            }
            log.append(s);
        }

        synchronized String logText() {
            return log.toString();
        }
    }

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
                    callback.onResult(true, stateJson().toString(), null);
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            callback.onResult(false, null, e.getMessage());
            return true;
        }
    }

    private JSONObject stateJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("running", job.running);
        o.put("done", job.done);
        o.put("ok", job.ok);
        o.put("log", job.logText());
        if (job.error != null) {
            o.put("error", job.error);
        }
        if (job.result != null) {
            o.put("result", job.result);
        }
        return o;
    }

    private void startDeploy(JSONObject opts, PackCallback callback) {
        if (job.running) {
            callback.onResult(false, null, "已有一个部署在进行中");
            return;
        }
        job.reset();
        callback.onResult(true, "{\"started\":true}", null);
        executor.execute(() -> runDeploy(opts));
    }

    private void runDeploy(JSONObject opts) {
        try {
            String host = opts.getString("host").trim();
            int sshPort = opts.optInt("sshPort", 22);
            String sshUser = opts.optString("sshUser", "root");
            String sshPassword = opts.getString("sshPassword");
            String adminUser = opts.getString("adminUser");
            String adminPass = opts.getString("adminPass");
            String subId = opts.optString("subId", "node1");
            int nodePort = opts.optInt("nodePort", 443);

            File script = new File(appContext.getFilesDir(), "packs/nodeforge/deploy.sh");
            if (!script.isFile()) {
                throw new IllegalStateException("功能包内缺少 deploy.sh，包可能损坏，请删包重装");
            }
            byte[] scriptBytes = Files.readAllBytes(script.toPath());

            job.append("== 连接 " + sshUser + "@" + host + ":" + sshPort + " …\n");
            JSch jsch = new JSch();
            Session session = jsch.getSession(sshUser, host, sshPort);
            session.setPassword(sshPassword);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(60_000);
            session.connect(30_000);
            try {
                if (session.getHostKey() != null) {
                    job.append("== 服务器指纹 " + session.getHostKey().getFingerPrint(jsch) + "\n");
                }
                String env = "NF_ADMIN_USER=" + shq(adminUser)
                        + " NF_ADMIN_PASS=" + shq(adminPass)
                        + " NF_SUB_ID=" + shq(subId)
                        + " NF_NODE_PORT=" + nodePort
                        + " NF_SSH_PORT=" + sshPort;
                ChannelExec channel = (ChannelExec) session.openChannel("exec");
                channel.setCommand("env " + env + " bash -s");
                channel.setInputStream(new ByteArrayInputStream(scriptBytes));
                // stdout 自己读（进日志+结果解析）；stderr 折进同一份全文，保持顺序近似。
                final StringBuilder fullText = new StringBuilder();
                channel.setErrStream(new java.io.OutputStream() {
                    @Override
                    public void write(int b) {
                        write(new byte[]{(byte) b}, 0, 1);
                    }

                    @Override
                    public void write(byte[] b, int off, int len) {
                        String s = new String(b, off, len, StandardCharsets.UTF_8);
                        synchronized (fullText) {
                            fullText.append(s);
                        }
                        job.append(s);
                    }
                });
                InputStream in = channel.getInputStream();
                channel.connect(15_000);
                try {
                    byte[] buf = new byte[4096];
                    while (true) {
                        while (in.available() > 0) {
                            int n = in.read(buf, 0, Math.min(buf.length, in.available()));
                            if (n < 0) break;
                            String s = new String(buf, 0, n, StandardCharsets.UTF_8);
                            synchronized (fullText) {
                                fullText.append(s);
                            }
                            job.append(s);
                        }
                        if (channel.isClosed()) {
                            if (in.available() <= 0) break;
                        } else {
                            Thread.sleep(200);
                        }
                    }
                    int exit = channel.getExitStatus();
                    String full;
                    synchronized (fullText) {
                        full = fullText.toString();
                    }
                    // 结果解析：最后一行 NF_RESULT / NF_ERROR 为准
                    String resultJson = null;
                    String errorLine = null;
                    for (String line : full.split("\n")) {
                        if (line.startsWith("NF_RESULT ")) {
                            resultJson = line.substring("NF_RESULT ".length()).trim();
                        } else if (line.startsWith("NF_ERROR:")) {
                            errorLine = line.substring("NF_ERROR:".length()).trim();
                        }
                    }
                    if (resultJson != null && exit == 0) {
                        job.result = new JSONObject(resultJson);
                        job.ok = true;
                        job.append("== 部署成功\n");
                    } else {
                        job.ok = false;
                        job.error = errorLine != null ? errorLine
                                : "远端脚本异常退出（exit=" + exit + "），细节看日志末尾";
                        job.append("== 部署失败：" + job.error + "\n");
                    }
                } finally {
                    channel.disconnect();
                }
            } finally {
                session.disconnect();
            }
        } catch (Exception e) {
            job.ok = false;
            job.error = e.getMessage() != null ? e.getMessage() : e.toString();
            job.append("== 部署失败：" + job.error + "\n");
        } finally {
            job.running = false;
            job.done = true;
        }
    }

    /** shell 单引号安全引用（密码可含任意字符）。 */
    private static String shq(String v) {
        return "'" + v.replace("'", "'\\''") + "'";
    }

    @Override
    public void release() {
        executor.shutdownNow();
    }
}
