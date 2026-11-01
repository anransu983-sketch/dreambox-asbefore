package com.getcapacitor.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Capacitor 兼容层：仅作标记用（插件 id 以入口路由表为准）。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CapacitorPlugin {
    String name() default "";

    Permission[] permissions() default {};
}
