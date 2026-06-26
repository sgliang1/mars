package com.interstellar.admin.common;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 管理端操作日志切面
 * 自动记录标注了 @AdminAudit 的 Controller 方法
 */
@Aspect
@Component
public class AdminAuditAspect {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Around("@annotation(com.interstellar.admin.common.AdminAudit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            AdminAudit audit = method.getAnnotation(AdminAudit.class);

            if (audit == null) return result;

            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

            String adminId = request.getHeader("X-User-Id");
            String adminName = request.getHeader("X-User-Name");
            String ip = getClientIp(request);

            // 从路径参数提取 targetId
            String targetId = extractTargetId(joinPoint, signature);

            jdbcTemplate.update(
                    "INSERT INTO admin_audit_log (admin_id, admin_username, action, target_type, target_id, detail, ip, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    adminId != null ? Long.parseLong(adminId) : 0,
                    adminName != null ? java.net.URLDecoder.decode(adminName, "UTF-8") : "unknown",
                    audit.action(),
                    audit.targetType(),
                    targetId != null ? Long.parseLong(targetId) : null,
                    audit.description(),
                    ip,
                    LocalDateTime.now()
            );
        } catch (Exception e) {
            // 日志记录失败不影响业务
        }

        return result;
    }

    private String extractTargetId(ProceedingJoinPoint joinPoint, MethodSignature signature) {
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < paramNames.length; i++) {
            if ("id".equals(paramNames[i]) || paramNames[i].endsWith("Id")) {
                if (args[i] instanceof Number) {
                    return args[i].toString();
                }
            }
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}