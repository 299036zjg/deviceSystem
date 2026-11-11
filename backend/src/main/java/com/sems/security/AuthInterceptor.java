package com.sems.security;

import com.sems.common.BizException;
import com.sems.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 令牌鉴权拦截器：除登录接口外所有 /api/v1 接口都必须携带有效令牌（设计文档 5.1、5.2）。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SessionStore sessionStore;

    public AuthInterceptor(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = extractToken(request);
        Session session = sessionStore.resolve(token)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "未登录或令牌已失效，请重新登录"));
        AuthContext.set(session);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
