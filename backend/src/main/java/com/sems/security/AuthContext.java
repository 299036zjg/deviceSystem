package com.sems.security;

import com.sems.common.BizException;
import com.sems.common.ErrorCode;
import com.sems.domain.Role;

/**
 * 当前请求的登录上下文：身份只来自服务端令牌，不接受客户端传入的操作人（BR10、NFR04）。
 */
public final class AuthContext {

    private static final ThreadLocal<Session> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(Session session) {
        HOLDER.set(session);
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Session current() {
        Session session = HOLDER.get();
        if (session == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return session;
    }

    public static Long userId() {
        return current().userId();
    }

    public static Role role() {
        return current().role();
    }

    public static boolean isAdmin() {
        return role() == Role.ADMIN;
    }

    public static boolean isReporter() {
        return role() == Role.REPORTER;
    }

    public static boolean isTechnician() {
        return role() == Role.TECHNICIAN;
    }

    public static void requireAdmin() {
        if (!isAdmin()) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, "仅管理员可执行该操作");
        }
    }

    public static void requireReporter() {
        if (!isReporter()) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, "仅报修人可提交故障报修");
        }
    }

    public static void requireTechnician() {
        if (!isTechnician()) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, "仅维修人员可处理工单");
        }
    }
}
