package com.getcapacitor.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Capacitor 兼容层：标记权限回调方法。兼容层 Plugin.requestPermissionForAlias
 * 按方法名反射调用被标记的方法（名字由调用方传入），本注解只为源码原样编译。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PermissionCallback {
}
