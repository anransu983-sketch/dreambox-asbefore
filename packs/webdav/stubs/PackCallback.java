package com.suanran.dreambox.core.pack;

/**
 * 编译桩（勿打进 plugins.dex）：与宿主 core 的 Kotlin fun interface
 * PackCallback 等价的 Java 镜像，仅供 javac 编译期引用；运行时由
 * DexClassLoader 的父优先加载解析到宿主本体里的真类。
 */
public interface PackCallback {
    void onResult(boolean success, String dataJson, String error);
}
