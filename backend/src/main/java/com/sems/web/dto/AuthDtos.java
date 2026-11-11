package com.sems.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 登录与账号会话相关 DTO（EM001、EM009）。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "账号不能为空") String account,
            @NotBlank(message = "密码不能为空") String password) {
    }

    public record LoginResponse(
            String token,
            Long userId,
            String account,
            String name,
            String department,
            String role,
            String roleLabel,
            LocalDateTime expireAt,
            boolean mustChangePassword) {
    }

    public record ProfileView(
            Long userId,
            String account,
            String name,
            String department,
            String role,
            String roleLabel,
            boolean enabled,
            boolean mustChangePassword) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "原密码不能为空") String oldPassword,
            @NotBlank(message = "新密码不能为空") String newPassword) {
    }
}
