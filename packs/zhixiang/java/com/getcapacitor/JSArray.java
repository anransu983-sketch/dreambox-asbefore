package com.getcapacitor;

import org.json.JSONArray;

/**
 * Capacitor 兼容层：以 org.json.JSONArray 为底的 JSArray 子集
 * （芷湘插件只用到 put 与 toString）。
 */
public class JSArray {

    private final JSONArray array;

    public JSArray() {
        this.array = new JSONArray();
    }

    public JSArray(JSONArray array) {
        this.array = array == null ? new JSONArray() : array;
    }

    public JSONArray toJSONArray() {
        return array;
    }

    public JSArray put(Object value) {
        array.put(JSObject.convert(value));
        return this;
    }

    public int length() {
        return array.length();
    }

    @Override
    public String toString() {
        return array.toString();
    }
}
