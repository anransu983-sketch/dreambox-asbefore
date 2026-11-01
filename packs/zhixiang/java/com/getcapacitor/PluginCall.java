package com.getcapacitor;

import com.suanran.dreambox.core.pack.PackCallback;

import org.json.JSONObject;

/**
 * Capacitor 兼容层：把宿主的功能包桥回调（PackCallback + 一段 options JSON）
 * 包成芷湘插件熟悉的 PluginCall。
 *
 * - resolve / reject 与官方一样以第一次终结为准之外的重复调用直接转发；
 *   芷湘各插件每个方法都只终结一次，这里不另加状态机。
 * - reject 的 code 不单独过桥：宿主的错误信封只有 message 一个字段，
 *   且芷湘 Web 层是按 message 文案展示/判断的，因此原样传 message。
 * - setKeepAlive 仅记录标志：宿主桥是长连接回调模型，异步回调
 *   （OkHttp enqueue 等）在 resolve 前不会被宿主回收，无需额外保活动作。
 */
public class PluginCall {

    private final JSObject data;
    private final PackCallback callback;
    private boolean keepAlive;

    public PluginCall(String optionsJson, PackCallback callback) {
        JSObject parsed;
        try {
            parsed = new JSObject(new JSONObject(optionsJson == null ? "{}" : optionsJson));
        } catch (Exception e) {
            parsed = new JSObject();
        }
        this.data = parsed;
        this.callback = callback;
    }

    public JSObject getData() {
        return data;
    }

    public String getString(String name) {
        return getString(name, null);
    }

    public String getString(String name, String defaultValue) {
        Object value = data.toJSON().opt(name);
        return value instanceof String ? (String) value : defaultValue;
    }

    public Integer getInt(String name) {
        Object value = data.toJSON().opt(name);
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    public Boolean getBoolean(String name) {
        return getBoolean(name, null);
    }

    public Boolean getBoolean(String name, Boolean defaultValue) {
        Object value = data.toJSON().opt(name);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        Boolean value = getBoolean(name, (Boolean) null);
        return value == null ? defaultValue : value;
    }

    public JSObject getObject(String name) {
        Object value = data.toJSON().opt(name);
        return value instanceof JSONObject ? new JSObject((JSONObject) value) : null;
    }

    public JSArray getArray(String name) {
        Object value = data.toJSON().opt(name);
        return value instanceof org.json.JSONArray ? new JSArray((org.json.JSONArray) value) : null;
    }

    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public boolean isKeptAlive() {
        return keepAlive;
    }

    public void resolve() {
        callback.onResult(true, "{}", null);
    }

    public void resolve(JSObject result) {
        callback.onResult(true, result == null ? "{}" : result.toString(), null);
    }

    public void reject(String message) {
        reject(message, null, null);
    }

    public void reject(String message, String code) {
        reject(message, code, null);
    }

    public void reject(String message, String code, Throwable error) {
        callback.onResult(false, null, message == null ? "插件调用失败" : message);
    }
}
