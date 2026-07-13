package org.template.interceptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.template.util.JwtUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * JWT 拦截器 — 拦截请求，校验 Bearer token 是否有效且未过期
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** Redis 中存储已注销 token 的 Set 的 key */
    private static final String LOGOUT_TOKEN_SET_KEY = "logout:token";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // 放行 OPTIONS 请求（CORS 预检）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 从请求头获取 Authorization
        String authHeader = request.getHeader("Authorization");

        // 验证格式：必须为 "Bearer <token>"
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setContentType("application/json;charset=utf-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":401,\"msg\":\"未提供有效的认证令牌\"}");
            return false;
        }

        // 提取 token（去掉 "Bearer " 前缀）
        String token = authHeader.substring(7);

        // 检查 token 是否已被登出（Redis 黑名单）
        Boolean isLoggedOut = stringRedisTemplate.opsForSet().isMember(LOGOUT_TOKEN_SET_KEY, token);
        if (Boolean.TRUE.equals(isLoggedOut)) {
            response.setContentType("application/json;charset=utf-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":401,\"msg\":\"令牌已失效，请重新登录\"}");
            return false;
        }

        // 检查 token 是否过期
        if (jwtUtil.isTokenExpired(token)) {
            response.setContentType("application/json;charset=utf-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":401,\"msg\":\"令牌已过期，请重新登录\"}");
            return false;
        }

        // 校验 token 签名是否合法
        if (!jwtUtil.validateToken(token)) {
            response.setContentType("application/json;charset=utf-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":401,\"msg\":\"无效的认证令牌\"}");
            return false;
        }

        // 将用户信息存入 request 属性，方便后续控制器使用
        request.setAttribute("userId", jwtUtil.getUserIdFromToken(token));
        request.setAttribute("account", jwtUtil.getAccountFromToken(token));

        return true;
    }
}
