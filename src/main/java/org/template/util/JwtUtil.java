package org.template.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类 — 生成 & 校验 Token
 */
@Component
public class JwtUtil {

    /** 签名密钥（生产环境应放在配置文件中，不要硬编码） */
    private final SecretKey secretKey;

    /** Token 过期时间（毫秒），默认 7 天 */
    private final long expiration;

    public JwtUtil() {
        // 生产环境建议从配置文件读取，这里用固定密钥保证重启后 token 仍有效
        String secret = "NoteBackbondSecretKey2026!@#$%^&*()VeryLongEnoughHS256";
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = 7 * 24 * 60 * 60 * 1000L; // 7 天
    }

    /**
     * 获取 Token 过期时间（毫秒）
     */
    public long getExpirationTime() {
        return expiration;
    }

    /**
     * 生成 token
     *
     * @param userId 用户 ID
     * @param account 用户账号
     * @return JWT token 字符串
     */
    public String generateToken(int userId, String account) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setId(String.valueOf(userId))
                .setSubject(account)
                .setIssuedAt(now)
                .setExpiration(expireDate)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 校验 token 是否有效（格式 + 签名 + 过期）
     *
     * @param token token 字符串
     * @return true 有效，false 无效
     */
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 从 token 中提取用户 ID
     *
     * @param token token 字符串
     * @return 用户 ID，无效返回 0
     */
    public int getUserIdFromToken(String token) {
        try {
            Claims claims = getClaims(token);
            return Integer.parseInt(claims.getId());
        } catch (JwtException | IllegalArgumentException e) {
            return 0;
        }
    }

    /**
     * 从 token 中提取账号
     *
     * @param token token 字符串
     * @return 账号，无效返回 null
     */
    public String getAccountFromToken(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 判断 token 是否已过期
     *
     * @param token token 字符串
     * @return true 已过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }

    /**
     * 解析 token，获取 Claims
     */
    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
