package com.sems.web;

import com.sems.common.ApiResponse;
import com.sems.common.PageResult;
import com.sems.service.UserService;
import com.sems.web.dto.UserDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 账号管理接口（EM001）：新增、停用、启用、重置密码。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<PageResult<UserDtos.UserView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(userService.list(keyword, role, department, enabled, page, size));
    }

    @GetMapping("/technicians")
    public ApiResponse<List<UserDtos.UserView>> technicians() {
        return ApiResponse.ok(userService.technicians());
    }

    @PostMapping
    public ApiResponse<UserDtos.UserView> create(@Valid @RequestBody UserDtos.CreateRequest request) {
        return ApiResponse.ok(userService.create(request));
    }

    @PostMapping("/{userId}/disable")
    public ApiResponse<UserDtos.UserView> disable(@PathVariable Long userId) {
        return ApiResponse.ok(userService.setEnabled(userId, false));
    }

    @PostMapping("/{userId}/enable")
    public ApiResponse<UserDtos.UserView> enable(@PathVariable Long userId) {
        return ApiResponse.ok(userService.setEnabled(userId, true));
    }

    @PostMapping("/{userId}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long userId,
                                           @Valid @RequestBody UserDtos.ResetPasswordRequest request) {
        userService.resetPassword(userId, request.password());
        return ApiResponse.ok();
    }
}
