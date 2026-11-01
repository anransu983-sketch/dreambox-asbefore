package com.getcapacitor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Capacitor 兼容层：仅作标记用。功能包入口的 dispatch 是显式路由表，
 * 不靠反射扫描本注解；保留它是为了让芷湘原插件源码原样编译。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PluginMethod {
    String returnType() default "promise";
}
