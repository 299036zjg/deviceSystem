package com.sems.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：把各类异常统一转换为 {code, message, data} 响应，
 * 并同时给出 HTTP 状态码（设计文档 5.6）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException ex, HttpServletRequest request) {
        log.info("业务异常 {} {} -> [{}] {}", request.getMethod(), request.getRequestURI(),
                ex.getErrorCode().code(), ex.getMessage());
        return ResponseEntity.status(ex.getErrorCode().httpStatus())
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null
                ? ErrorCode.PARAM_INVALID.message()
                : (fieldError.getDefaultMessage() == null || fieldError.getDefaultMessage().isBlank()
                    ? fieldError.getField() + " 不合法"
                    : fieldError.getDefaultMessage());
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.httpStatus())
                .body(ApiResponse.error(ErrorCode.PARAM_INVALID, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream().findFirst()
                .map(v -> v.getMessage()).orElse(ErrorCode.PARAM_INVALID.message());
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.httpStatus())
                .body(ApiResponse.error(ErrorCode.PARAM_INVALID, message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        log.info("请求格式错误: {}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.httpStatus())
                .body(ApiResponse.error(ErrorCode.PARAM_INVALID, "请求参数格式不正确，请检查字段类型与必填项"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(ErrorCode.FILE_LIMIT.httpStatus())
                .body(ApiResponse.error(ErrorCode.FILE_LIMIT, "上传文件超过大小限制：单张图片不超过 5 MB"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("数据完整性冲突: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(ErrorCode.STATE_CONFLICT.httpStatus())
                .body(ApiResponse.error(ErrorCode.STATE_CONFLICT, "数据已存在或状态已变化，请刷新后重试"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        if (ex.getResourcePath() != null && ex.getResourcePath().startsWith("api/")) {
            return ResponseEntity.status(ErrorCode.NOT_FOUND.httpStatus())
                    .body(ApiResponse.error(ErrorCode.NOT_FOUND, "接口不存在：" + ex.getResourcePath()));
        }
        return ResponseEntity.status(404).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex, HttpServletRequest request) {
        log.error("未处理异常 {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "服务器内部错误，请稍后重试"));
    }
}
