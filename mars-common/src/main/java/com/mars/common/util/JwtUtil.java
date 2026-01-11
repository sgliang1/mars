package com.mars.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 * 修改说明：
 * 1. 添加 @Component 注解，交给 Spring 管理
 * 2. 使用 @Value 注入配置文件中的 mars.jwt.secret
 * 3. 去除硬编码的静态密钥
 */
@Component
public class JwtUtil {

    // 静态变量存储密钥，供静态方法使用
    private static SecretKey KEY;

    // Token 过期时间：24小时 (86400000毫秒)
    private static final long EXPIRATION_TIME = 86400000;

    /**
     * 利用 Spring 的 Setter 注入特性，将配置文件的值赋给静态变量
     * 配置文件需配置：mars.jwt.secret=你的复杂密钥字符串
     */
    @Value("${mars.jwt.secret}")
    public void setSecret(String secret) {
        JwtUtil.KEY = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Token
     */
    public static String generateToken(Long userId, String username) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("username", username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(KEY)
                .compact();
    }

    /**
     * 解析 Token
     */
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}