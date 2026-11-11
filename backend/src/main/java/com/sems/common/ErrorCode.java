package com.sems.common;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码，对应设计文档 5.6 节。
 */
public enum ErrorCode {

    SUCCESS(0, "成功", HttpStatus.OK),

    PARAM_INVALID(40001, "参数校验失败", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40101, "未登录或令牌失效", HttpStatus.UNAUTHORIZED),
    ROLE_FORBIDDEN(40301, "角色无权执行该操作", HttpStatus.FORBIDDEN),
    SCOPE_FORBIDDEN(40302, "超出数据范围", HttpStatus.FORBIDDEN),
    NOT_FOUND(40401, "对象不存在", HttpStatus.NOT_FOUND),
    STATE_CONFLICT(40901, "状态已变化或重复提交", HttpStatus.CONFLICT),
    EQUIPMENT_DISABLED(40902, "设备已停用", HttpStatus.CONFLICT),
    FILE_LIMIT(41301, "上传文件超过大小或数量限制", HttpStatus.PAYLOAD_TOO_LARGE),
    ILLEGAL_TRANSITION(42201, "非法状态转换", HttpStatus.UNPROCESSABLE_ENTITY),
    INTERNAL_ERROR(50001, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
