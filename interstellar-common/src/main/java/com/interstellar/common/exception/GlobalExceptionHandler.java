package com.interstellar.common.exception;

import com.interstellar.common.Result;
import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 所有异常日志均携带 traceId，便于跨服务问题定位
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private String traceId() {
        return Span.current().getSpanContext().getTraceId();
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}, traceId={}", e.getCode(), e.getMessage(), traceId());
        return ResponseEntity.status(e.getCode()).body(Result.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}, traceId={}", msg, traceId());
        return ResponseEntity.badRequest().body(Result.error(400, msg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数错误: {}, traceId={}", e.getMessage(), traceId());
        return ResponseEntity.badRequest().body(Result.error(400, e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限不足: {}, traceId={}", e.getMessage(), traceId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.error(403, "权限不足"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthentication(AuthenticationException e) {
        log.warn("认证失败: {}, traceId={}", e.getMessage(), traceId());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(401, "未登录或认证已过期"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("服务器内部错误, traceId={}", traceId(), e);
        return ResponseEntity.internalServerError().body(Result.error(500, "服务器内部错误"));
    }
}