package com.mars.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private static SecretKey KEY;

    // Access token: 2 hours
    private static final long ACCESS_EXPIRATION_TIME = 2 * 60 * 60 * 1000L;

    // Refresh token: 30 days
    private static final long REFRESH_EXPIRATION_TIME = 30L * 24 * 60 * 60 * 1000;

    @Value("${mars.jwt.secret}")
    public void setSecret(String secret) {
        JwtUtil.KEY = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ==================== Access Token ====================

    public static String generateToken(Long userId, String username) {
        return generateToken(userId, username, "user");
    }

    public static String generateToken(Long userId, String username, String role) {
        var builder = Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("username", username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_EXPIRATION_TIME));
        if (role != null && !role.isBlank()) {
            builder.claim("role", role);
        }
        return builder.signWith(KEY).compact();
    }

    // ==================== Refresh Token ====================

    /**
     * 生成刷新令牌（30天过期，带 type=refresh 标识）
     */
    public static String generateRefreshToken(Long userId, String username) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("username", username)
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + REFRESH_EXPIRATION_TIME))
                .signWith(KEY)
                .compact();
    }

    // ==================== 通用解析 ====================

    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 获取 token 类型："access" 或 "refresh"
     */
    public static String getTokenType(String token) {
        Claims claims = parseToken(token);
        Object type = claims.get("type");
        return type != null ? type.toString() : "access";
    }
}
