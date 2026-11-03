package com.suanran.pack.sect;

import android.content.Context;

import com.suanran.dreambox.core.pack.PackBridgeDispatcher;
import com.suanran.dreambox.core.pack.PackCallback;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * 高武门派插件入口：选修、修为、境界与全 App 换装全部走宿主 HostSect 桥
 * （pack.json 声明 hostSect），本类只满足宿主的包契约，让插件能被加载与打开。
 */
public class SectPackEntry implements PackBridgeDispatcher {

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
