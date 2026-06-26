package com.interstellar.admin.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理端操作日志注解
 * 标记在 Controller 方法上，自动记录操作日志
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminAudit {
    /**
     * 操作类型
     */
    String action();

    /**
     * 目标类型（可从方法参数中推断）
     */
    String targetType() default "";

    /**
     * 操作描述模板
     */
    String description() default "";
}