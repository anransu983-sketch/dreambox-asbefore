package com.suanran.pack.rebirth;

import android.content.Context;

import com.suanran.dreambox.core.pack.PackBridgeDispatcher;
import com.suanran.dreambox.core.pack.PackCallback;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * 轮回插件入口：转世、配点与前世碑林全部走宿主 HostRebirth 桥
 * （pack.json 声明 hostRebirth，账本与门派桥共用宿主存储），本类只满足宿主的包契约。
 */
public class RebirthPackEntry implements PackBridgeDispatcher {

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
