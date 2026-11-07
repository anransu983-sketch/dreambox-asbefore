package com.suanran.pack.sshtty;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import com.suanran.dreambox.core.pack.PackBridgeDispatcher;
import com.suanran.dreambox.core.pack.PackCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * VPS 终端功能包入口：JuiceSSH 式 SSH 终端。
 * - 服务器列表落盘加密保存（AES-256-GCM，密钥在 AndroidKeyStore），
 *   存 filesDir/sshtty-data/（功能包卸载不误删），密码永不回传网页、列表只给安全视图。
 * - 迁移备份（v11）：AndroidKeyStore 主密钥不进任何文件备份、卸载重装/换机即失，
 *   故另给一条可迁移路：用户口令经 PBKDF2 派生密钥、AES-GCM 加密导出全部条目
 *   （含密码/私钥/主机指纹）为一段文本，新机粘贴+口令导入即恢复（合并去重）。
 * - 连接走 JSch shell 通道 + PTY（xterm-256color），输出经宿主 emit 实时推给网页的
 *   xterm.js；输入/改尺寸走桥方法。主机指纹 TOFU：首次记录、之后比对、不一致断开报错。
 * - 同时只保留一个会话（v1）；release()/disconnect 即断开。
 */
public class SshTermPackEntry implements PackBridgeDispatcher {

    private static final String PLUGIN = "SshTerm";
    private static final String KEY_ALIAS = "sshtty.store.v1";
    private static final String PORTABLE_MAGIC = "SSHTTY-BACKUP";
    private static final int PORTABLE_ITER = 120000;

    private static Context appContext;
    private Function3<? super String, ? super String, ? super String, Unit> emitFn;

    private final ExecutorService io = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sshtty-io");
        t.setDaemon(true);
        return t;
    });

    // ── 会话状态 ─────────────────────────────────────────────
    private volatile Session session;
    private volatile ChannelShell channel;
    private volatile OutputStream chanOut;
    private volatile boolean alive = false;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private Thread flusher;

    @Override
    public void init(Context context, Function3<? super String, ? super String, ? super String, Unit> emit) {
        appContext = context.getApplicationContext();
        emitFn = emit;
    }

    @Override
    public void release() {
        disconnectInternal();
    }

    @Override
    public boolean dispatch(String pluginId, String method, String optionsJson, PackCallback callback) {
        if (!PLUGIN.equals(pluginId)) {
            return false;
        }
        final JSONObject opts;
        try {
            opts = new JSONObject(optionsJson == null ? "{}" : optionsJson);
        } catch (Exception e) {
            callback.onResult(false, null, "参数解析失败");
            return true;
        }
        switch (method == null ? "" : method) {
            case "listServers":
                io.execute(() -> {
                    try {
                        callback.onResult(true, resultWithServers(loadStore()).toString(), null);
                    } catch (Exception e) {
                        callback.onResult(false, null, "读取保存的服务器失败：" + msg(e));
                    }
                });
                return true;
            case "saveServer":
                io.execute(() -> {
                    try {
                        JSONObject store = loadStore();
                        upsertServer(store, opts);
                        saveStore(store);
                        callback.onResult(true, resultWithServers(store).toString(), null);
                    } catch (Exception e) {
                        callback.onResult(false, null, "保存失败：" + msg(e));
                    }
                });
                return true;
            case "genKeyPair":
                io.execute(() -> {
                    try {
                        callback.onResult(true, doGenKeyPair().toString(), null);
                    } catch (Exception e) {
                        callback.onResult(false, null, "生成密钥失败：" + msg(e));
                    }
                });
                return true;
            case "getPublicKey":
                io.execute(() -> {
                    try {
                        callback.onResult(true, doGetPublicKey(opts).toString(), null);
                    } catch (Exception e) {
                        callback.onResult(false, null, "读取公钥失败：" + msg(e));
                    }
                });
                return true;
            case "deleteServer":
                io.execute(() -> {
                    try {
                        JSONObject store = loadStore();
                        removeServer(store, opts.optString("id", ""));
                        saveStore(store);
                        callback.onResult(true, resultWithServers(store).toString(), null);
                    } catch (Exception e) {
                        callback.onResult(false, null, "删除失败：" + msg(e));
                    }
                });
                return true;
            case "exportBackup":
                io.execute(() -> {
                    try {
                        callback.onResult(true, doExportBackup(opts).toString(), null);
                    } catch (Exception e) {
                        callback.onResult(false, null, "导出迁移包失败：" + msg(e));
                    }
                });
                return true;
            case "importBackup":
                io.execute(() -> {
                    try {
                        callback.onResult(true, doImportBackup(opts).toString(), null);
                    } catch (Exception e) {
                        callback.onResult(false, null, "导入迁移包失败：" + msg(e));
                    }
                });
                return true;
            case "connect":
                io.execute(() -> doConnect(opts, callback));
                return true;
            case "termInput":
                writeInput(opts.optString("d", ""));
                callback.onResult(true, "{}", null);
                return true;
            case "termResize":
                resize(opts.optInt("cols", 80), opts.optInt("rows", 24));
                callback.onResult(true, "{}", null);
                return true;
            case "disconnect":
                disconnectInternal();
                callback.onResult(true, "{}", null);
                return true;
            case "tqStart":
                io.execute(() -> doTqStart(opts, callback));
                return true;
            case "tqState":
                io.execute(() -> doTqState(opts, callback));
                return true;
            case "ecsStart":
                io.execute(() -> doEcsStart(opts, callback));
                return true;
            case "ecsState":
                io.execute(() -> doEcsState(opts, callback));
                return true;
            case "ipqStart":
                io.execute(() -> doIpqStart(opts, callback));
                return true;
            case "ipqState":
                io.execute(() -> doIpqState(opts, callback));
                return true;
            case "nqStart":
                io.execute(() -> doNqStart(opts, callback));
                return true;
            case "nqState":
                io.execute(() -> doNqState(opts, callback));
                return true;
            case "tuneStart":
                io.execute(() -> doTuneStart(opts, callback));
                return true;
            case "tuneState":
                io.execute(() -> doTuneState(opts, callback));
                return true;
            case "tuneRollback":
                io.execute(() -> doTuneRollback(opts, callback));
                return true;
            default:
                return false;
        }
    }

    // ── 加密存储 ─────────────────────────────────────────────

    private File storeFile() {
        File dir = new File(appContext.getFilesDir(), "sshtty-data");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, "store.json.enc");
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

    private synchronized JSONObject loadStore() throws Exception {
        File f = storeFile();
        if (!f.exists()) {
            return new JSONObject().put("servers", new JSONArray()).put("hostKeys", new JSONObject());
        }
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
        String plain = new String(c.doFinal(ct), StandardCharsets.UTF_8);
        JSONObject store = new JSONObject(plain);
        if (!store.has("servers")) store.put("servers", new JSONArray());
        if (!store.has("hostKeys")) store.put("hostKeys", new JSONObject());
        return store;
    }

    private synchronized void saveStore(JSONObject store) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, storeKey());
        byte[] ct = c.doFinal(store.toString().getBytes(StandardCharsets.UTF_8));
        JSONObject env = new JSONObject()
                .put("iv", Base64.encodeToString(c.getIV(), Base64.NO_WRAP))
                .put("ct", Base64.encodeToString(ct, Base64.NO_WRAP));
        File f = storeFile();
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(env.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    // ── 迁移备份（口令加密，不依赖 AndroidKeyStore，换机/卸载重装可恢复） ──

    private SecretKey derivePortableKey(String pw, byte[] salt, int iter) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(pw.toCharArray(), salt, iter, 256);
        return new SecretKeySpec(f.generateSecret(spec).getEncoded(), "AES");
    }

    private JSONObject doExportBackup(JSONObject opts) throws Exception {
        String pw = opts.optString("password", "");
        if (pw.length() < 8) throw new Exception("备份口令至少 8 位");
        JSONObject store = loadStore();
        JSONArray arr = store.optJSONArray("servers");
        if (arr == null || arr.length() == 0) throw new Exception("还没有保存的服务器，先添加再导出");
        SecureRandom rnd = new SecureRandom();
        byte[] salt = new byte[16];
        rnd.nextBytes(salt);
        byte[] iv = new byte[12];
        rnd.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, derivePortableKey(pw, salt, PORTABLE_ITER), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(store.toString().getBytes(StandardCharsets.UTF_8));
        JSONObject blob = new JSONObject()
                .put("magic", PORTABLE_MAGIC)
                .put("v", 1)
                .put("kdf", "PBKDF2WithHmacSHA256")
                .put("iter", PORTABLE_ITER)
                .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                .put("ct", Base64.encodeToString(ct, Base64.NO_WRAP));
        return new JSONObject().put("blob", blob.toString()).put("count", arr.length());
    }

    private JSONObject doImportBackup(JSONObject opts) throws Exception {
        String pw = opts.optString("password", "");
        if (pw.length() == 0) throw new Exception("请输入备份口令");
        String text = opts.optString("blob", "").trim();
        if (text.length() == 0) throw new Exception("请先粘贴迁移包");
        JSONObject blob;
        try {
            blob = new JSONObject(text);
        } catch (Exception e) {
            throw new Exception("迁移包格式不对：请粘贴完整的导出内容");
        }
        if (!PORTABLE_MAGIC.equals(blob.optString("magic", "")) || blob.optInt("v", 0) != 1) {
            throw new Exception("这不是 sshtty 的迁移包");
        }
        int iter = blob.optInt("iter", PORTABLE_ITER);
        if (iter < 10000 || iter > 1000000) throw new Exception("迁移包参数异常");
        byte[] salt = Base64.decode(blob.optString("salt", ""), Base64.DEFAULT);
        byte[] iv = Base64.decode(blob.optString("iv", ""), Base64.DEFAULT);
        byte[] ct = Base64.decode(blob.optString("ct", ""), Base64.DEFAULT);
        byte[] plain;
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, derivePortableKey(pw, salt, iter), new GCMParameterSpec(128, iv));
            plain = c.doFinal(ct);
        } catch (Exception e) {
            throw new Exception("备份口令错误，或迁移包已损坏");
        }
        JSONObject imported;
        try {
            imported = new JSONObject(new String(plain, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new Exception("迁移包内容损坏");
        }
        JSONArray impArr = imported.optJSONArray("servers");
        if (impArr == null) throw new Exception("迁移包里没有服务器数据");

        JSONObject cur = loadStore();
        JSONArray curArr = cur.optJSONArray("servers");
        if (curArr == null) {
            curArr = new JSONArray();
            cur.put("servers", curArr);
        }
        Map<String, Integer> byId = new HashMap<>();
        Map<String, Integer> byEndpoint = new HashMap<>();
        for (int i = 0; i < curArr.length(); i++) {
            JSONObject s = curArr.optJSONObject(i);
            if (s == null) continue;
            if (s.optString("id", "").length() > 0) byId.put(s.optString("id"), i);
            byEndpoint.put(endpointKey(s), i);
        }
        int importedCount = 0;
        for (int i = 0; i < impArr.length(); i++) {
            JSONObject s = impArr.optJSONObject(i);
            if (s == null || s.optString("host", "").trim().length() == 0) continue;
            if (s.optString("id", "").length() == 0) s.put("id", UUID.randomUUID().toString());
            Integer idx = byId.get(s.optString("id"));
            if (idx == null) idx = byEndpoint.get(endpointKey(s));
            if (idx != null) {
                curArr.put(idx, s);
            } else {
                idx = curArr.length();
                curArr.put(s);
            }
            byId.put(s.optString("id"), idx);
            byEndpoint.put(endpointKey(s), idx);
            importedCount++;
        }
        JSONObject impKeys = imported.optJSONObject("hostKeys");
        if (impKeys != null) {
            JSONObject curKeys = cur.optJSONObject("hostKeys");
            if (curKeys == null) {
                curKeys = new JSONObject();
                cur.put("hostKeys", curKeys);
            }
            JSONArray names = impKeys.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String k = names.optString(i);
                    curKeys.put(k, impKeys.opt(k));
                }
            }
        }
        saveStore(cur);
        JSONObject out = resultWithServers(cur);
        out.put("imported", importedCount);
        out.put("total", curArr.length());
        return out;
    }

    private static String endpointKey(JSONObject s) {
        return s.optString("host", "") + "|" + s.optInt("port", 22) + "|" + s.optString("user", "");
    }

    private JSONObject resultWithServers(JSONObject store) throws Exception {
        JSONArray arr = store.optJSONArray("servers");
        JSONArray safe = new JSONArray();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject s = arr.optJSONObject(i);
                if (s == null) continue;
                safe.put(new JSONObject()
                        .put("id", s.optString("id"))
                        .put("name", s.optString("name"))
                        .put("host", s.optString("host"))
                        .put("port", s.optInt("port", 22))
                        .put("user", s.optString("user"))
                        .put("bw", s.optInt("bw", 1000))
                        .put("hasPassword", s.optString("password").length() > 0)
                        .put("hasKey", s.optString("privateKey").length() > 0)
                        .put("authMode", s.optString("authMode", "password")));
            }
        }
        return new JSONObject().put("servers", safe);
    }

    private void upsertServer(JSONObject store, JSONObject o) throws Exception {
        JSONArray arr = store.optJSONArray("servers");
        if (arr == null) {
            arr = new JSONArray();
            store.put("servers", arr);
        }
        String id = o.optString("id", "");
        JSONObject target = null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i);
            if (s != null && id.length() > 0 && id.equals(s.optString("id"))) {
                target = s;
                break;
            }
        }
        if (target == null) {
            target = new JSONObject().put("id", UUID.randomUUID().toString());
            arr.put(target);
        }
        String host = o.optString("host", "").trim();
        if (host.length() == 0) throw new Exception("IP/域名不能为空");
        target.put("host", host);
        target.put("name", o.optString("name", "").trim());
        target.put("port", o.optInt("port", 22));
        target.put("user", o.optString("user", "root").trim());
        int bw = o.optInt("bw", 0);
        if (bw > 0) target.put("bw", bw);
        else if (!target.has("bw")) target.put("bw", 1000);
        String pw = o.optString("password", "");
        if (pw.length() > 0) {
            target.put("password", pw);
        } else if (!target.has("password")) {
            target.put("password", "");
        }
        String mode = o.optString("authMode", "");
        if (mode.length() > 0) {
            target.put("authMode", mode);
        } else if (!target.has("authMode")) {
            target.put("authMode", "password");
        }
        if (o.optBoolean("clearKey", false)) {
            target.put("privateKey", "");
            target.put("keyPassphrase", "");
        }
        String newKey = o.optString("privateKey", "");
        if (newKey.length() > 0) {
            // 换钥：口令按本次表单为准（无口令即空），先校验可解析/口令对再落盘
            String pass = o.optString("keyPassphrase", "");
            validateKey(newKey, pass);
            target.put("privateKey", newKey);
            target.put("keyPassphrase", pass);
        } else if (!target.has("privateKey")) {
            target.put("privateKey", "");
            target.put("keyPassphrase", "");
        } else if (o.optString("keyPassphrase", "").length() > 0) {
            // 只改口令：拿已存私钥校验一遍再存
            validateKey(target.optString("privateKey"), o.optString("keyPassphrase", ""));
            target.put("keyPassphrase", o.optString("keyPassphrase", ""));
        }
    }

    private void removeServer(JSONObject store, String id) throws Exception {
        JSONArray arr = store.optJSONArray("servers");
        if (arr == null || id.length() == 0) return;
        JSONArray kept = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i);
            if (s != null && !id.equals(s.optString("id"))) kept.put(s);
        }
        store.put("servers", kept);
    }

    private JSONObject findServer(JSONObject store, String id) {
        JSONArray arr = store.optJSONArray("servers");
        if (arr == null) return null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i);
            if (s != null && id.equals(s.optString("id"))) return s;
        }
        return null;
    }

    // ── SSH 会话 ─────────────────────────────────────────────

    // ── 私钥认证 ─────────────────────────────────────────

    private void validateKey(String pem, String passphrase) throws Exception {
        JSch j = new JSch();
        KeyPair kp = null;
        try {
            kp = KeyPair.load(j, pem.getBytes(StandardCharsets.UTF_8), null);
            if (kp == null) throw new Exception("私钥无法解析，请确认粘贴的是完整的私钥文本");
            if (kp.isEncrypted()) {
                if (passphrase == null || passphrase.length() == 0) {
                    throw new Exception("这把私钥有口令，请填写私钥口令");
                }
                if (!kp.decrypt(passphrase.getBytes(StandardCharsets.UTF_8))) {
                    throw new Exception("私钥口令不正确");
                }
            }
        } catch (com.jcraft.jsch.JSchException e) {
            throw new Exception("私钥无法解析：" + msg(e));
        } finally {
            if (kp != null) kp.dispose();
        }
    }

    private String publicKeyLine(KeyPair kp) {
        byte[] blob = kp.getPublicKeyBlob();
        int n = ((blob[0] & 0xff) << 24) | ((blob[1] & 0xff) << 16)
                | ((blob[2] & 0xff) << 8) | (blob[3] & 0xff);
        String type = new String(blob, 4, n, StandardCharsets.UTF_8);
        return type + " " + Base64.encodeToString(blob, Base64.NO_WRAP) + " sshtty";
    }

    private JSONObject doGenKeyPair() throws Exception {
        JSch j = new JSch();
        KeyPair kp = null;
        try {
            try {
                kp = KeyPair.genKeyPair(j, KeyPair.ED25519);
            } catch (Exception e) {
                kp = KeyPair.genKeyPair(j, KeyPair.RSA, 2048);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            kp.writePrivateKey(baos);
            return new JSONObject()
                    .put("privateKey", baos.toString("UTF-8"))
                    .put("publicKey", publicKeyLine(kp));
        } finally {
            if (kp != null) kp.dispose();
        }
    }

    private JSONObject doGetPublicKey(JSONObject opts) throws Exception {
        String pem = opts.optString("privateKey", "");
        String pass = opts.optString("keyPassphrase", "");
        if (pem.length() == 0) {
            JSONObject s = findServer(loadStore(), opts.optString("id", ""));
            if (s == null) throw new Exception("保存的服务器不存在");
            pem = s.optString("privateKey", "");
            if (pass.length() == 0) pass = s.optString("keyPassphrase", "");
            if (pem.length() == 0) throw new Exception("这台服务器还没有保存私钥");
        }
        JSch j = new JSch();
        KeyPair kp = null;
        try {
            kp = KeyPair.load(j, pem.getBytes(StandardCharsets.UTF_8), null);
            if (kp == null) throw new Exception("私钥无法解析");
            if (kp.isEncrypted()) {
                if (pass.length() == 0
                        || !kp.decrypt(pass.getBytes(StandardCharsets.UTF_8))) {
                    throw new Exception("私钥口令不正确或未填写");
                }
            }
            return new JSONObject().put("publicKey", publicKeyLine(kp));
        } finally {
            if (kp != null) kp.dispose();
        }
    }

    /** 有私钥时先挂 identity（JSch 默认先试 publickey 后 password），密码兜底。 */
    private void applyAuth(JSch jsch, Session ss, String keyPem, String keyPass, String pw)
            throws Exception {
        if (keyPem != null && keyPem.trim().length() > 0) {
            byte[] pass = (keyPass != null && keyPass.length() > 0)
                    ? keyPass.getBytes(StandardCharsets.UTF_8) : null;
            jsch.addIdentity("sshtty-" + UUID.randomUUID().toString(),
                    keyPem.getBytes(StandardCharsets.UTF_8), null, pass);
        }
        if (pw != null && pw.length() > 0) ss.setPassword(pw);
    }

    private void doConnect(JSONObject opts, PackCallback callback) {
        try {
            String host, user, pw, keyPem, keyPass, authMode;
            int port;
            String label;
            String id = opts.optString("id", "");
            if (id.length() > 0) {
                JSONObject s = findServer(loadStore(), id);
                if (s == null) {
                    callback.onResult(false, null, "保存的服务器不存在（可能已删除）");
                    return;
                }
                host = s.optString("host");
                port = s.optInt("port", 22);
                user = s.optString("user", "root");
                pw = s.optString("password", "");
                keyPem = s.optString("privateKey", "");
                keyPass = s.optString("keyPassphrase", "");
                authMode = s.optString("authMode", "password");
                label = s.optString("name", "");
                if (label.length() == 0) label = host;
            } else {
                host = opts.optString("host", "").trim();
                port = opts.optInt("port", 22);
                user = opts.optString("user", "root");
                pw = opts.optString("password", "");
                keyPem = opts.optString("privateKey", "");
                keyPass = opts.optString("keyPassphrase", "");
                authMode = opts.optString("authMode", "password");
                label = host;
            }
            if (host.length() == 0) {
                callback.onResult(false, null, "IP/域名不能为空");
                return;
            }
            disconnectInternal();

            if ("key".equals(authMode) && keyPem.trim().length() == 0) {
                callback.onResult(false, null, "已选私钥登录，但这台服务器还没有私钥（先在编辑页生成或粘贴一把）");
                return;
            }
            JSch jsch = new JSch();
            Session ss = jsch.getSession(user, host, port);
            applyAuth(jsch, ss, "password".equals(authMode) ? "" : keyPem, keyPass, pw);
            java.util.Properties cfg = new java.util.Properties();
            cfg.put("StrictHostKeyChecking", "no");
            ss.setConfig(cfg);
            ss.connect(20000);

            // TOFU 指纹校验
            try {
                HostKey hk = ss.getHostKey();
                String fp = hk == null ? "" : hk.getFingerPrint(jsch);
                if (fp != null && fp.length() > 0) {
                    JSONObject store = loadStore();
                    JSONObject keys = store.optJSONObject("hostKeys");
                    if (keys == null) {
                        keys = new JSONObject();
                        store.put("hostKeys", keys);
                    }
                    String hkKey = host + ":" + port;
                    String known = keys.optString(hkKey, "");
                    if (known.length() > 0 && !known.equals(fp)) {
                        if (opts.optBoolean("acceptNewHostKey", false)) {
                            // 用户已在页面确认信任新指纹：更新记录，继续本次连接。
                            keys.put(hkKey, fp);
                            saveStore(store);
                        } else {
                            ss.disconnect();
                            callback.onResult(false, null,
                                    "主机指纹与上次记录不一致，已断开防冒充。\n记录：" + known + "\n现在：" + fp);
                            return;
                        }
                    }
                    if (known.length() == 0) {
                        keys.put(hkKey, fp);
                        saveStore(store);
                    }
                }
            } catch (Exception ignore) {
                // 指纹读取失败不阻断连接（TOFU 是加固项，不是连接前提）
            }

            ChannelShell ch = (ChannelShell) ss.openChannel("shell");
            ch.setPtyType("xterm-256color");
            int cols = opts.optInt("cols", 80);
            int rows = opts.optInt("rows", 24);
            ch.setPtySize(cols, rows, cols * 9, rows * 17);
            InputStream in = ch.getInputStream();
            chanOut = ch.getOutputStream();
            ch.connect(15000);
            session = ss;
            channel = ch;
            alive = true;
            startFlusher();
            Thread reader = new Thread(() -> readLoop(in), "sshtty-reader");
            reader.setDaemon(true);
            reader.start();
            callback.onResult(true, new JSONObject().put("connected", true).put("name", label).toString(), null);
        } catch (Exception e) {
            disconnectInternal();
            callback.onResult(false, null, "连接失败：" + msg(e));
        }
    }

    private void readLoop(InputStream in) {
        byte[] buf = new byte[8192];
        try {
            int n;
            while ((n = in.read(buf)) != -1) {
                if (n > 0) {
                    synchronized (pending) {
                        pending.write(buf, 0, n);
                    }
                }
            }
        } catch (Exception ignore) {
        } finally {
            boolean was = alive;
            alive = false;
            flushPending();
            if (was) emit("termClosed", new JSONObject().toString());
            try {
                if (channel != null) channel.disconnect();
            } catch (Exception ignore) {
            }
            try {
                if (session != null) session.disconnect();
            } catch (Exception ignore) {
            }
        }
    }

    private void startFlusher() {
        if (flusher != null && flusher.isAlive()) return;
        flusher = new Thread(() -> {
            while (alive) {
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    return;
                }
                flushPending();
            }
        }, "sshtty-flush");
        flusher.setDaemon(true);
        flusher.start();
    }

    private void flushPending() {
        byte[] data;
        synchronized (pending) {
            if (pending.size() == 0) return;
            data = pending.toByteArray();
            pending.reset();
        }
        try {
            JSONObject payload = new JSONObject()
                    .put("d", Base64.encodeToString(data, Base64.NO_WRAP));
            emit("termData", payload.toString());
        } catch (Exception ignore) {
        }
    }

    private void emit(String event, String dataJson) {
        Function3<? super String, ? super String, ? super String, Unit> f = emitFn;
        if (f != null) {
            try {
                f.invoke(PLUGIN, event, dataJson);
            } catch (Exception ignore) {
            }
        }
    }

    private void writeInput(String b64) {
        OutputStream out = chanOut;
        if (out == null || !alive || b64 == null || b64.length() == 0) return;
        try {
            byte[] data = Base64.decode(b64, Base64.NO_WRAP);
            synchronized (this) {
                out.write(data);
                out.flush();
            }
        } catch (Exception ignore) {
        }
    }

    private void resize(int cols, int rows) {
        ChannelShell ch = channel;
        if (ch != null && alive && cols > 0 && rows > 0) {
            try {
                ch.setPtySize(cols, rows, cols * 9, rows * 17);
            } catch (Exception ignore) {
            }
        }
    }

    private synchronized void disconnectInternal() {
        alive = false;
        try {
            if (channel != null) channel.disconnect();
        } catch (Exception ignore) {
        }
        try {
            if (session != null) session.disconnect();
        } catch (Exception ignore) {
        }
        channel = null;
        session = null;
        chanOut = null;
        synchronized (pending) {
            pending.reset();
        }
    }

    // ── 一键 TQ（TcpQuality，ibsgss）──────────────────────────
    // 完整脚本口径（用户 2026-11-02 21:32 拍板）：--all 全套
    // （IPv4/IPv6＋教育网 CERNET＋国际互联＋单线程测速）。脱离会话跑（nohup 写日志），
    // 状态/报告链接从日志里取——报告 ID 有 0/O 形近坑，必须机器原文提取、原样可复制。

    private JSONObject credsFor(JSONObject opts) throws Exception {
        String id = opts.optString("id", "");
        JSONObject s = id.length() > 0 ? findServer(loadStore(), id) : null;
        if (s == null) throw new Exception("保存的服务器不存在（可能已删除）");
        return s;
    }

    private String runExec(JSONObject srv, String command, int timeoutSec) throws Exception {
        JSch jsch = new JSch();
        Session ss = jsch.getSession(srv.optString("user", "root"),
                srv.optString("host"), srv.optInt("port", 22));
        String kPem = "password".equals(srv.optString("authMode", "password"))
                ? "" : srv.optString("privateKey", "");
        applyAuth(jsch, ss, kPem, srv.optString("keyPassphrase", ""),
                srv.optString("password", ""));
        java.util.Properties cfg = new java.util.Properties();
        cfg.put("StrictHostKeyChecking", "no");
        ss.setConfig(cfg);
        ss.connect(20000);
        try {
            com.jcraft.jsch.ChannelExec ch =
                    (com.jcraft.jsch.ChannelExec) ss.openChannel("exec");
            ch.setCommand(command);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            ch.setOutputStream(out);
            ch.setErrStream(err);
            ch.connect(15000);
            long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
            while (!ch.isClosed()) {
                if (System.currentTimeMillis() > deadline) {
                    throw new Exception("远端命令超时");
                }
                try {
                    Thread.sleep(120);
                } catch (InterruptedException ie) {
                    throw new Exception("被中断");
                }
            }
            // 关流前等泵线程把尾巴写完
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignore) {
            }
            ch.disconnect();
            String s = out.toString("UTF-8");
            String e = err.toString("UTF-8").trim();
            if (s.trim().length() == 0 && e.length() > 0) s = e;
            return s;
        } finally {
            ss.disconnect();
        }
    }

    private void doTqStart(JSONObject opts, PackCallback callback) {
        try {
            JSONObject srv = credsFor(opts);
            // 存活判定用 pidfile（v1 踩坑：pgrep -f 模式会匹配到 exec 自身的命令行
            // ——命令文本里就含 runTcpQuality.sh 字样，误报 ALREADY 永不启动）。
            String cmd = "mkdir -p /tmp/sshtty-tq && cd /tmp/sshtty-tq && "
                    + "if [ -f tq.pid ] && kill -0 $(cat tq.pid) 2>/dev/null; then echo ALREADY; "
                    + "else (curl -fsSL -o runTcpQuality.sh "
                    + "https://raw.githubusercontent.com/ibsgss/TcpQuality/main/runTcpQuality.sh "
                    + "|| curl -fsSL -o runTcpQuality.sh https://tcpquality.ibsgss.uk/run) && "
                    + "mv -f tq.log tq-prev.log 2>/dev/null; "
                    + "nohup env TERM=xterm-256color bash runTcpQuality.sh --all "
                    + "> tq.log 2>&1 < /dev/null & echo $! > tq.pid; echo STARTED; fi";
            String r = runExec(srv, cmd, 90).trim();
            callback.onResult(true, new JSONObject()
                    .put("started", r.contains("STARTED"))
                    .put("alreadyRunning", r.contains("ALREADY"))
                    .put("echo", r)
                    .toString(), null);
        } catch (Exception e) {
            callback.onResult(false, null, "启动 TQ 失败：" + msg(e));
        }
    }

    // 脚本日志带 ANSI 颜色码（融合怪/IP 体检尤其多），回给 WebView 前洗掉，
    // 否则日志里满屏 [0;36m 之类的转义残片。顺带去掉回车。
    private static String stripAnsi(String s) {
        if (s == null) return "";
        return s.replaceAll("\u001B\\[[0-9;?]*[ -/]*[@-~]", "")
                .replaceAll("\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)", "")
                .replaceAll("\u001B[@-Z\\\\-_]", "")
                .replace("\r", "");
    }

    private void doTqState(JSONObject opts, PackCallback callback) {
        try {
            JSONObject srv = credsFor(opts);
            String cmd = "if [ -f /tmp/sshtty-tq/tq.pid ] && kill -0 $(cat /tmp/sshtty-tq/tq.pid) 2>/dev/null; "
                    + "then echo RUNNING; else echo IDLE; fi; "
                    + "grep -aoE 'https://tcpquality[.]ibsgss[.]uk/r/[A-Za-z0-9_-]+' "
                    + "/tmp/sshtty-tq/tq.log 2>/dev/null | tail -1; "
                    + "echo __TAIL__; tail -c 1500 /tmp/sshtty-tq/tq.log 2>/dev/null";
            String r = runExec(srv, cmd, 30);
            boolean running = r.startsWith("RUNNING");
            String url = "";
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("https://tcpquality\\.ibsgss\\.uk/r/[A-Za-z0-9_\\-]+")
                    .matcher(r);
            while (m.find()) url = m.group();
            String tail = "";
            int idx = r.indexOf("__TAIL__");
            if (idx >= 0) tail = stripAnsi(r.substring(idx + "__TAIL__".length()).trim());
            callback.onResult(true, new JSONObject()
                    .put("running", running)
                    .put("url", url)
                    .put("tail", tail)
                    .toString(), null);
        } catch (Exception e) {
            callback.onResult(false, null, "读取 TQ 状态失败：" + msg(e));
        }
    }

    // ── 一键融合怪（ecs.sh，spiritysdx）────────────────────────
    // 参数模式 -m 1 = 主菜单 1「顺序测试完全体」（全项目），参数模式下镜像询问
    // 自动通过，全程无交互。与 TQ 同款：脱离会话跑（nohup 写日志）、pidfile 判
    // 存活（不用 pgrep -f，防匹配到 exec 自身命令行）、状态从日志尾取。

    private void doEcsStart(JSONObject opts, PackCallback callback) {
        try {
            JSONObject srv = credsFor(opts);
            String cmd = "mkdir -p /tmp/sshtty-ecs && cd /tmp/sshtty-ecs && "
                    + "if [ -f ecs.pid ] && kill -0 $(cat ecs.pid) 2>/dev/null; then echo ALREADY; "
                    + "else (curl -fsSL -o ecs.sh "
                    + "https://gitlab.com/spiritysdx/za/-/raw/main/ecs.sh "
                    + "|| wget -qO ecs.sh https://gitlab.com/spiritysdx/za/-/raw/main/ecs.sh) && "
                    + "mv -f ecs.log ecs-prev.log 2>/dev/null; "
                    + "nohup env TERM=xterm-256color bash ecs.sh -m 1 "
                    + "> ecs.log 2>&1 < /dev/null & echo $! > ecs.pid; echo STARTED; fi";
            String r = runExec(srv, cmd, 90).trim();
            callback.onResult(true, new JSONObject()
                    .put("started", r.contains("STARTED"))
                    .put("alreadyRunning", r.contains("ALREADY"))
                    .put("echo", r)
                    .toString(), null);
        } catch (Exception e) {
            callback.onResult(false, null, "启动融合怪失败：" + msg(e));
        }
    }

    private void doEcsState(JSONObject opts, PackCallback callback) {
        try {
            JSONObject srv = credsFor(opts);
            String cmd = "if [ -f /tmp/sshtty-ecs/ecs.pid ] && kill -0 $(cat /tmp/sshtty-ecs/ecs.pid) 2>/dev/null; "
                    + "then echo RUNNING; else echo IDLE; fi; "
                    + "grep -aoE '(h)?paste[.]spiritlhl[.]net/#/show/[A-Za-z0-9]+' "
                    + "/tmp/sshtty-ecs/ecs.log 2>/dev/null | tail -1; "
                    + "echo __TAIL__; tail -c 1500 /tmp/sshtty-ecs/ecs.log 2>/dev/null";
            String r = runExec(srv, cmd, 30);
            boolean running = r.startsWith("RUNNING");
            // 融合怪跑完默认生成分享短链（paste.spiritlhl.net/#/show/<id>，http/https
            // 同 id）；从日志里提 id，统一拼 https 版回给页面做链接卡。
            String url = "";
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?:h)?paste\\.spiritlhl\\.net/#/show/([A-Za-z0-9]+)")
                    .matcher(r);
            while (m.find()) url = "https://paste.spiritlhl.net/#/show/" + m.group(1);
            String tail = "";
            int idx = r.indexOf("__TAIL__");
            if (idx >= 0) tail = stripAnsi(r.substring(idx + "__TAIL__".length()).trim());
            callback.onResult(true, new JSONObject()
                    .put("running", running)
                    .put("url", url)
                    .put("tail", tail)
                    .toString(), null);
        } catch (Exception e) {
            callback.onResult(false, null, "读取融合怪状态失败：" + msg(e));
        }
    }

    // ── 一键 IP 体检（IPQuality，xykt）─────────────────────────
    // 不带 -f：脚本默认在报告里给 IP 打码（-f 恰恰是「报告展示完整 IP」，发帖分享会露 IP）；-y 自动同意安装缺失依赖（脚本唯一的交互点）。IP.Check.Place
    // 本身 302 跳到 xykt/IPQuality 的 ip.sh，这里直下 GitHub 原文件、短链兜底。
    // 脱离会话与状态读取与 TQ 同款。

    private void doIpqStart(JSONObject opts, PackCallback callback) {
        try {
            JSONObject srv = credsFor(opts);
            String cmd = "mkdir -p /tmp/sshtty-ipq && cd /tmp/sshtty-ipq && "
                    + "if [ -f ipq.pid ] && kill -0 $(cat ipq.pid) 2>/dev/null; then echo ALREADY; "
                    + "else (curl -fsSL -o ipq.sh "
                    + "https://raw.githubusercontent.com/xykt/IPQuality/main/ip.sh "
                    + "|| curl -sL -o ipq.sh https://IP.Check.Place) && "
                    + "mv -f ipq.log ipq-prev.log 2>/dev/null; "
                    + "nohup env TERM=xterm-256color bash ipq.sh -y "
                    + "> ipq.log 2>&1 < /dev/null & echo $! > ipq.pid; echo STARTED; fi";
            String r = runExec(srv, cmd, 90).trim();
            callback.onResult(true, new JSONObject()
                    .put("started", r.contains("STARTED"))
                    .put("alreadyRunning", r.contains("ALREADY"))
                    .put("echo", r)
                    .toString(), null);
        } catch (Exception e) {
            callback.onResult(false, null, "启动 IP 体检失败：" + msg(e));
        }
    }

    private void doIpqState(JSONObject opts, PackCallback callback) {
        try {
            JSONObject srv = credsFor(opts);
            String cmd = "if [ -f /tmp/sshtty-ipq/ipq.pid ] && kill -0 $(cat /tmp/sshtty-ipq/ipq.pid) 2>/dev/null; "
                    + "then echo RUNNING; else echo IDLE; fi; "
                    + "grep -aoE 'Report[.]Check[.]Place/ip/[A-Za-z0-9]+[.]svg' "
                    + "/tmp/sshtty-ipq/ipq.log 2>/dev/null | tail -1; "
                    + "echo __TAIL__; tail -c 1500 /tmp/sshtty-ipq/ipq.log 2>/dev/null";
            String r = runExec(srv, cmd, 30);
            boolean running = r.startsWith("RUNNING");
            // 体检跑完在日志尾打印报告链接（Report.Check.Place/ip/<id>.svg），
            // 提出来回给页面做链接卡（复制/打开），与 TQ 同款。
            String url = "";
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("Report\\.Check\\.Place/ip/[A-Za-z0-9]+\\.svg")
                    .matcher(r);
            while (m.find()) url = "https://" + m.group();
            String tail = "";
            int idx = r.indexOf("__TAIL__");
            if (idx >= 0) tail = stripAnsi(r.substring(idx + "__TAIL__".length()).trim());
            callback.onResult(true, new JSONObject()
                    .put("running", running)
                    .put("url", url)
                    .put("tail", tail)
                    .toString(), null);
        } catch (Exception e) {
            callback.onResult(false, null, "读取 IP 体检状态失败：" + msg(e));
        }
    }

    // ── 一键 NodeQuality（LloydAsp/NodeQuality）──────────────────
    // 全家桶：硬件＋IP 质量＋网络质量＋回程路由，在 BenchOS 沙箱里跑、测完自清理。
    // 脚本开头有四道跑哪几项的选择题，stdin 接 /dev/null 时 read 读到空、
    // 每项都取默认 y，所以脱离会话跑即是无人值守全套；报告由服务端生成，
    // 链接打印在日志里，状态读取时提出来做链接卡（与 TQ/IP 体检同款）。

    private void doNqStart(JSONObject opts, PackCallback callback) {
        try {
            JSONObject srv = credsFor(opts);
            String cmd = "mkdir -p /tmp/sshtty-nq && cd /tmp/sshtty-nq && "
                    + "if [ -f nq.pid ] && kill -0 $(cat nq.pid) 2>/dev/null; then echo ALREADY; "
                    + "else (curl -fsSL -o nq.sh https://run.nodequality.com) && "
                    + "mv -f nq.log nq-prev.log 2>/dev/null; "
                    + "nohup env TERM=xterm-256color bash nq.sh "
                    + "> nq.log 2>&1 < /dev/null & echo $! > nq.pid; echo STARTED; fi";
            String r = runExec(srv, cmd, 90).trim();
            callback.onResult(true, new JSONObject()
                    .put("started", r.contains("STARTED"))
                    .put("alreadyRunning", r.contains("ALREADY"))
                    .put("echo", r)
                    .toString(), null);
        } catch (Exception e) {
            callback.onResult(false, null, "启动 NodeQuality 失败：" + msg(e));
        }
    }

    private void doNqState(JSONObject opts, PackCallback callback) {
        try {
            JSONObject srv = credsFor(opts);
            String cmd = "if [ -f /tmp/sshtty-nq/nq.pid ] && kill -0 $(cat /tmp/sshtty-nq/nq.pid) 2>/dev/null; "
                    + "then echo RUNNING; else echo IDLE; fi; "
                    + "grep -aoE 'nodequality[.]com/r/[A-Za-z0-9]+' "
                    + "/tmp/sshtty-nq/nq.log 2>/dev/null | tail -1; "
                    + "echo __TAIL__; tail -c 1500 /tmp/sshtty-nq/nq.log 2>/dev/null";
            String r = runExec(srv, cmd, 30);
            boolean running = r.startsWith("RUNNING");
            String url = "";
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("nodequality\\.com/r/[A-Za-z0-9]+")
                    .matcher(r);
            while (m.find()) url = "https://" + m.group();
            String tail = "";
            int idx = r.indexOf("__TAIL__");
            if (idx >= 0) tail = stripAnsi(r.substring(idx + "__TAIL__".length()).trim());
            callback.onResult(true, new JSONObject()
                    .put("running", running)
                    .put("url", url)
                    .put("tail", tail)
                    .toString(), null);
        } catch (Exception e) {
            callback.onResult(false, null, "读取 NodeQuality 状态失败：" + msg(e));
        }
    }

    // ── 一键调优（Tcpfit，Kylin010/tcpfit）─────────────────────
    // 按 2026-10-23 源码审计定版口径（报告 ~/workspace/tcpfit-audit/AUDIT.md）：
    // 固定 tag v0.5.9、下载后先验 sha256（与仓库 SHA256SUMS 一致的那枚）不过不跑；
    // 关遥测（no-telemetry 文件＋环境变量）；只跑基础调优 CLI
    // tune --role proxy --bw <套餐带宽>（不跑 iperf3、不做整形），约 1 分钟；
    // 调前留底（qdisc/路由/关键 sysctl）写进日志头；回滚走 tcpfit rollback。

    private static final String TCPFIT_SHA256 =
            "8331cc40950229a3280ce32406330a85b1a3d21ba398a4db3dc7e25c39783741";
    private static final String TCPFIT_URL =
            "https://raw.githubusercontent.com/Kylin010/tcpfit/v0.5.9/tcpfit.sh";

    private void doTuneStart(JSONObject opts, PackCallback callback) {
        try {
            JSONObject srv = credsFor(opts);
            int bw = srv.optInt("bw", 1000);
            if (bw < 10) bw = 1000;
            boolean full = "full".equals(opts.optString("mode", ""));
            String launch;
            if (full) {
                // Full pipeline in one detached bash -c (pidfile watches it):
                // ensure iperf3 -> basic tune -> pick first usable US-West Leaseweb peer ->
                // sweep --yes (knee hunt) -> shape at RECOMMEND rate if a knee was found.
                String inner = "command -v iperf3 >/dev/null 2>&1 || { apt-get update -qq; DEBIAN_FRONTEND=noninteractive apt-get install -y -qq iperf3; }; "
                        + "bash tcpfit.sh tune --role proxy --bw " + bw + "; "
                        + "peer=; for p in speedtest.lax12.us.leaseweb.net speedtest.sfo12.us.leaseweb.net speedtest.sea11.us.leaseweb.net speedtest.dal13.us.leaseweb.net; do "
                        + "if timeout 25 iperf3 -c $p -t 2 >/dev/null 2>&1; then peer=$p; break; fi; done; "
                        + "if [ -z \"$peer\" ]; then echo NOPEER-all-public-peers-unavailable; else echo sweep-peer=$peer; "
                        + "bash tcpfit.sh sweep --peer $peer --nominal " + bw + " --yes; "
                        + "rate=$(grep ^RECOMMEND= /var/lib/tcpfit/sweep.result 2>/dev/null | cut -d= -f2); "
                        + "if [ -n \"$rate\" ]; then echo apply-shape=${rate}Mbit; bash tcpfit.sh shape --rate $rate; "
                        + "else echo no-knee-found-skip-shape; fi; fi; echo FULLDONE";
                launch = "nohup env TCPFIT_NO_TELEMETRY=1 TERM=xterm-256color bash -c '" + inner + "' "
                        + ">> tune.log 2>&1 < /dev/null & "
                        + "echo $! > tune.pid; echo STARTED; ";
            } else {
                launch = "nohup env TCPFIT_NO_TELEMETRY=1 TERM=xterm-256color bash tcpfit.sh "
                        + "tune --role proxy --bw " + bw + " >> tune.log 2>&1 < /dev/null & "
                        + "echo $! > tune.pid; echo STARTED; ";
            }
            String cmd = "mkdir -p /tmp/sshtty-tcpfit /var/lib/tcpfit && "
                    + "touch /var/lib/tcpfit/no-telemetry && cd /tmp/sshtty-tcpfit && "
                    + "if [ -f tune.pid ] && kill -0 $(cat tune.pid) 2>/dev/null; then echo ALREADY; else "
                    + "if [ ! -f tcpfit.sh ]; then curl -fsSL -o tcpfit.sh " + TCPFIT_URL + "; fi; "
                    + "if echo \"" + TCPFIT_SHA256 + "  tcpfit.sh\" | sha256sum -c - 2>/dev/null | grep -q OK; then "
                    + "mv -f tune.log tune-prev.log 2>/dev/null; "
                    + "{ echo '== 调前留底 =='; tc qdisc show 2>/dev/null; ip route show 2>/dev/null | head -5; "
                    + "sysctl net.ipv4.tcp_congestion_control net.core.rmem_max net.core.wmem_max 2>/dev/null; "
                    + "echo '== 开始调优 =='; } > tune.log; "
                    + launch
                    + "else echo BADSHA; fi; fi";
            String r = runExec(srv, cmd, 90).trim();
            callback.onResult(true, new JSONObject()
                    .put("started", r.contains("STARTED"))
                    .put("alreadyRunning", r.contains("ALREADY"))
                    .put("badSha", r.contains("BADSHA"))
                    .put("bw", bw)
                    .put("mode", full ? "full" : "basic")
                    .put("echo", r)
                    .toString(), null);
        } catch (Exception e) {
            callback.onResult(false, null, "启动调优失败：" + msg(e));
        }
    }

    private void doTuneState(JSONObject opts, PackCallback callback) {
        try {
            JSONObject srv = credsFor(opts);
            String cmd = "if [ -f /tmp/sshtty-tcpfit/tune.pid ] && kill -0 $(cat /tmp/sshtty-tcpfit/tune.pid) 2>/dev/null; "
                    + "then echo RUNNING; else echo IDLE; fi; "
                    + "echo __SYSCTL__; sysctl net.ipv4.tcp_congestion_control net.core.rmem_max net.core.wmem_max 2>/dev/null; "
                    + "tc qdisc show 2>/dev/null | head -4; grep ^RECOMMEND= /var/lib/tcpfit/sweep.result 2>/dev/null; "
                    + "echo __TAIL__; tail -c 1800 /tmp/sshtty-tcpfit/tune.log 2>/dev/null";
            String r = runExec(srv, cmd, 30);
            boolean running = r.startsWith("RUNNING");
            String sysctl = "";
            String tail = "";
            int i1 = r.indexOf("__SYSCTL__");
            int i2 = r.indexOf("__TAIL__");
            if (i1 >= 0 && i2 > i1) sysctl = r.substring(i1 + "__SYSCTL__".length(), i2).trim();
            if (i2 >= 0) tail = stripAnsi(r.substring(i2 + "__TAIL__".length()).trim());
            callback.onResult(true, new JSONObject()
                    .put("running", running)
                    .put("sysctl", sysctl)
                    .put("tail", tail)
                    .toString(), null);
        } catch (Exception e) {
            callback.onResult(false, null, "读取调优状态失败：" + msg(e));
        }
    }

    private void doTuneRollback(JSONObject opts, PackCallback callback) {
        try {
            JSONObject srv = credsFor(opts);
            String cmd = "cd /tmp/sshtty-tcpfit && "
                    + "if [ -f tune.pid ] && kill -0 $(cat tune.pid) 2>/dev/null; then echo BUSY; "
                    + "elif [ ! -f tcpfit.sh ]; then echo NOSCRIPT; "
                    + "elif echo \"" + TCPFIT_SHA256 + "  tcpfit.sh\" | sha256sum -c - 2>/dev/null | grep -q OK; then "
                    + "{ echo '== 回滚调优 =='; } >> tune.log; "
                    + "nohup env TCPFIT_NO_TELEMETRY=1 TERM=xterm-256color bash tcpfit.sh rollback "
                    + ">> tune.log 2>&1 < /dev/null & echo $! > tune.pid; echo STARTED; "
                    + "else echo BADSHA; fi";
            String r = runExec(srv, cmd, 60).trim();
            callback.onResult(true, new JSONObject()
                    .put("started", r.contains("STARTED"))
                    .put("busy", r.contains("BUSY"))
                    .put("badSha", r.contains("BADSHA"))
                    .put("noScript", r.contains("NOSCRIPT"))
                    .put("echo", r)
                    .toString(), null);
        } catch (Exception e) {
            callback.onResult(false, null, "启动回滚失败：" + msg(e));
        }
    }

    private String msg(Exception e) {
        String m = e.getMessage();
        return m == null || m.length() == 0 ? e.getClass().getSimpleName() : m;
    }
}
