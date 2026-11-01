package com.getcapacitor.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Capacitor 兼容层：随 @CapacitorPlugin 的 permissions 列表出现，仅作标记用。
 * 不写 @Target：它只作为其它注解的成员值出现。
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Permission {
    String alias() default "";

    String[] strings() default {};
}
