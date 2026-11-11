package com.sems.common;

/**
 * 统一响应结构：code、message、data，见设计文档 5.1 节。
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "成功", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "成功", null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message == null ? errorCode.message() : message, null);
    }

    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
