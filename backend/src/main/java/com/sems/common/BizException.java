package com.sems.common;

/**
 * 业务异常：由控制层或服务层抛出，统一由 {@link GlobalExceptionHandler} 转换为错误响应。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public static BizException param(String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message);
    }

    public static BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(ErrorCode.ROLE_FORBIDDEN, message);
    }

    public static BizException outOfScope(String message) {
        return new BizException(ErrorCode.SCOPE_FORBIDDEN, message);
    }

    public static BizException conflict(String message) {
        return new BizException(ErrorCode.STATE_CONFLICT, message);
    }

    public static BizException illegalTransition(String message) {
        return new BizException(ErrorCode.ILLEGAL_TRANSITION, message);
    }
}
