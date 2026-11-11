package com.sems.security;

import com.sems.domain.Role;

import java.time.LocalDateTime;

/**
 * 运行期会话对象（设计文档 3.5.4）：记录用户、角色、部门、有效期与令牌，不单独建表。
 */
public record Session(String token, Long userId, String name, Role role, String department, LocalDateTime expireAt) {

    public boolean expired() {
        return expireAt.isBefore(LocalDateTime.now());
    }
}
