package com.sems.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 账号管理 DTO（EM001）。
 */
public final class UserDtos {

    private UserDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "账号不能为空")
            @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "账号只能由 3 至 32 位字母、数字或下划线组成")
            String account,

            @NotBlank(message = "姓名不能为空")
            @Size(max = 50, message = "姓名不能超过 50 字符")
            String name,

            @NotBlank(message = "部门不能为空")
            @Size(max = 50, message = "部门不能超过 50 字符")
            String department,

            @NotBlank(message = "角色不能为空")
            String role,

            @NotBlank(message = "初始密码不能为空")
            String password) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "新密码不能为空") String password) {
    }

    public record UserView(
            Long userId,
            String account,
            String name,
            String department,
            String role,
            String roleLabel,
            boolean enabled,
            LocalDateTime createdAt) {
    }
}
