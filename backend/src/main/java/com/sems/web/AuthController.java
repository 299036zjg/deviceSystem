package com.sems.web;

import com.sems.common.ApiResponse;
import com.sems.service.AuthService;
import com.sems.web.dto.AuthDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴权与会话接口（设计文档 5.2、5.3）。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            authService.logout(header.substring(BEARER_PREFIX.length()).trim());
        }
        return ApiResponse.ok();
    }

    @GetMapping("/profile")
    public ApiResponse<AuthDtos.ProfileView> profile() {
        return ApiResponse.ok(authService.profile());
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        authService.changePassword(request);
        return ApiResponse.ok();
    }
}
