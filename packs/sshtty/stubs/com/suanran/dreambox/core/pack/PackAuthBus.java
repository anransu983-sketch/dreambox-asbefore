package com.suanran.dreambox.core.pack;

import android.content.Context;

/**
 * 编译桩（勿打进 plugins.dex）：与宿主 core 的 Kotlin object PackAuthBus
 * 等价的 Java 镜像，仅供 javac 编译期引用；运行时解析到宿主真类
 * （Kotlin object 的 INSTANCE 静态字段 + 实例方法形状与此一致）。
 * 桩方法永不真正执行，被调用即说明类加载出了问题，直接抛错。
 */
public final class PackAuthBus {
    public static final PackAuthBus INSTANCE = new PackAuthBus();

    private PackAuthBus() {
    }

    public void requestJava(Context activityContext, String url, String title, PackStringCallback done) {
        throw new UnsupportedOperationException("PackAuthBus stub must never run at runtime");
    }
}
