package com.suanran.dreambox.core.pack;

/**
 * 编译桩（勿打进 plugins.dex）：与宿主 core 的 Kotlin fun interface
 * PackStringCallback 等价的 Java 镜像，仅供 javac 编译期引用。
 */
public interface PackStringCallback {
    void onDone(String value);
}
