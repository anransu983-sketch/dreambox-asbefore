package com.suanran.dreambox.core.pack;

import android.content.Context;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * 编译桩（勿打进 plugins.dex）：与宿主 core 的 Kotlin interface
 * PackBridgeDispatcher 等价的 Java 镜像，仅供 javac 编译期引用；
 * 运行时由父优先类加载解析到宿主真类，因此方法描述符必须与 Kotlin
 * 编译产物逐字一致：
 * - init(Landroid/content/Context;Lkotlin/jvm/functions/Function3;)V
 * - dispatch(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;
 *     Lcom/suanran/dreambox/core/pack/PackCallback;)Z
 * - release()V（Kotlin 接口默认方法，Java 侧实现类自己给出即可）
 */
public interface PackBridgeDispatcher {
    void init(Context context, Function3<? super String, ? super String, ? super String, Unit> emit);

    boolean dispatch(String pluginId, String method, String optionsJson, PackCallback callback);

    void release();
}
