package com.ticketbooking.common.annotation;

import com.ticketbooking.common.enums.Role;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAuth {
    /**
     * 需要的角色，默认 USER 表示只需要登录即可
     */
    Role[] value() default {Role.USER};
}
