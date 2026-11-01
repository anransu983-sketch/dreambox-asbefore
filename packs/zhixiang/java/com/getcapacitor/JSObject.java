package com.getcapacitor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Capacitor 兼容层（功能包自带，不是官方 Capacitor）：以 org.json.JSONObject
 * 为底实现芷湘插件实际用到的 JSObject 子集。
 *
 * 与官方语义对齐的点：
 * - put(name, List) 转成 JSONArray（HttpBridge 的 setCookies /
 *   requestHeaderNames 等 ArrayList 字段靠这条到 JS 侧才是真数组）；
 * - put(name, JSObject / JSArray) 存其底层 JSON；
 * - optString / optLong / keys 与 org.json 同名方法一致。
 */
public class JSObject {

    private final JSONObject json;

    public JSObject() {
        this.json = new JSONObject();
    }

    public JSObject(JSONObject json) {
        this.json = json == null ? new JSONObject() : json;
    }

    public JSONObject toJSON() {
        return json;
    }

    public JSObject put(String name, Object value) {
        try {
            json.put(name, convert(value));
        } catch (JSONException ignored) {
            // JSONObject.put 对合法键值不抛；吞掉保持与官方 put 的不抛约定一致。
        }
        return this;
    }

    static Object convert(Object value) {
        if (value == null) {
            return JSONObject.NULL;
        }
        if (value instanceof JSObject) {
            return ((JSObject) value).json;
        }
        if (value instanceof JSArray) {
            return ((JSArray) value).toJSONArray();
        }
        if (value instanceof JSONObject || value instanceof JSONArray) {
            return value;
        }
        if (value instanceof List) {
            JSONArray array = new JSONArray();
            for (Object item : (List<?>) value) {
                array.put(convert(item));
            }
            return array;
        }
        if (value instanceof Map) {
            JSONObject object = new JSONObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                try {
                    object.put(String.valueOf(entry.getKey()), convert(entry.getValue()));
                } catch (JSONException ignored) {
                }
            }
            return object;
        }
        // String / Integer / Long / Double / Boolean 等直接交给 JSONObject。
        return value;
    }

    public String optString(String name) {
        return json.optString(name, null);
    }

    public String optString(String name, String fallback) {
        return json.optString(name, fallback);
    }

    public long optLong(String name, long fallback) {
        return json.optLong(name, fallback);
    }

    public boolean has(String name) {
        return json.has(name);
    }

    public Iterator<String> keys() {
        return json.keys();
    }

    @Override
    public String toString() {
        return json.toString();
    }
}
