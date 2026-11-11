package com.sems.domain;

import java.util.Arrays;

/**
 * 报修单状态（设计文档 BR01）：待受理、已驳回、已受理、已完成。
 * 数据库按设计文档 4.3 以中文字符串保存。
 */
public enum RequestStatus {

    PENDING("待受理"),
    REJECTED("已驳回"),
    ACCEPTED("已受理"),
    COMPLETED("已完成");

    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * 兼容前端传入枚举名（PENDING）或中文（待受理）两种写法。
     */
    public static RequestStatus from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(s -> s.name().equalsIgnoreCase(trimmed) || s.label.equals(trimmed))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的报修单状态：" + value));
    }
}
