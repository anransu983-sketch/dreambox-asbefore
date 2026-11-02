package com.suanran.pack.iptest;

import android.content.Context;

import com.suanran.dreambox.core.pack.PackBridgeDispatcher;
import com.suanran.dreambox.core.pack.PackCallback;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * IP 测试插件入口：页面是纯链接清单（13 个检测站），不需要原生能力，
 * 本类只是满足宿主的包契约，让插件能被加载与打开。
 */
public class IpTestPackEntry implements PackBridgeDispatcher {

    @Override
    public void init(Context context, Function3<? super String, ? super String, ? super String, Unit> emit) {
        // 无状态，无需初始化。
    }

    @Override
    public boolean dispatch(String pluginId, String method, String optionsJson, PackCallback callback) {
        return false;
    }

    @Override
    public void release() {
    }
}
