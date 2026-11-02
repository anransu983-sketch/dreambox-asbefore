package com.suanran.pack.kernel;

import android.content.Context;

import com.suanran.dreambox.core.pack.PackBridgeDispatcher;
import com.suanran.dreambox.core.pack.PackCallback;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * 内核管家插件入口（2026-11-03 从本体网络设置拆出）：
 * 本类只是宿主契约的占位实现——真正的列表/下载/切换/装自定义内核全部经
 * 宿主 HostKernel 桥（pack.json 声明 hostKernel）委托本体 KernelManager 执行，
 * 插件本身不碰内核文件。
 */
public class KernelPackEntry implements PackBridgeDispatcher {

    @Override
    public void init(Context context, Function3<? super String, ? super String, ? super String, Unit> emit) {
        // 无状态：一切走宿主桥。
    }

    @Override
    public void release() {
        // 无资源。
    }

    @Override
    public boolean dispatch(String pluginId, String method, String optionsJson, PackCallback callback) {
        return false;
    }
}
