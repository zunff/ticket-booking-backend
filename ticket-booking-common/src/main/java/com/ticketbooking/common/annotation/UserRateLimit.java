package com.ticketbooking.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@RequireAuth
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UserRateLimit {

    /**
     * Sentinel 资源名称，默认是 "类名:方法名"
     */
    String resourceName() default "";
}