package com.getcapacitor;

import android.app.Activity;
import android.content.Context;

import java.lang.reflect.Method;

/**
 * Capacitor 兼容层：芷湘插件的 Plugin 基类子集。
 *
 * - initForPack 由功能包入口在分发前注入宿主应用 Context 与事件出口；
 * - getActivity 恒为 null：包内没有 Activity（宿主的 PackHostActivity
 *   不对包开放），依赖 Activity 的原代码必须改走 Context 路线，
 *   具体改法逐个记在 PACK_NOTES.md；
 * - notifyListeners 直接转成宿主事件（宿主在 addListener 拦截时已登记
 *   JS 侧回调，包只管发）；
 * - requestPermissionForAlias：宿主在打开功能包时已按 pack.json 的
 *   permissions 统一申请过运行时权限，包内再走这条时无法再弹窗，
 *   因此直接回调用 @PermissionCallback 标注的回调方法，由它复查
 *   权限并 resolve / reject —— 与官方「授权结果回到回调」的形状一致，
 *   差别是多了一次宿主打开时的前置申请。
 */
public class Plugin {

    /** 包内事件出口：由入口实现，最终进宿主的 emit。 */
    public interface EventEmitter {
        void emit(String pluginId, String eventName, String dataJson);
    }

    private Context context;
    private EventEmitter emitter;
    private String pluginId = "";

    public void initForPack(Context context, EventEmitter emitter, String pluginId) {
        this.context = context;
        this.emitter = emitter;
        this.pluginId = pluginId == null ? "" : pluginId;
    }

    /** 与官方 Plugin.load 对齐的空实现，子类按需覆盖。 */
    public void load() {
    }

    public Context getContext() {
        return context;
    }

    /** 包内无 Activity，恒返回 null（见类注释）。 */
    public Activity getActivity() {
        return null;
    }

    protected void notifyListeners(String eventName, JSObject data) {
        EventEmitter current = emitter;
        if (current == null) {
            return;
        }
        current.emit(pluginId, eventName, data == null ? "{}" : data.toString());
    }

    protected void requestPermissionForAlias(String alias, PluginCall call, String callbackMethodName) {
        try {
            Method method = findCallbackMethod(getClass(), callbackMethodName);
            if (method == null) {
                call.reject("权限回调缺失", "NO_PERMISSION_CALLBACK");
                return;
            }
            method.setAccessible(true);
            method.invoke(this, call);
        } catch (Exception e) {
            call.reject("权限处理失败", "PERMISSION_CALLBACK_FAILED", e);
        }
    }

    private static Method findCallbackMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Plugin.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name)
                        && method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0] == PluginCall.class) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
