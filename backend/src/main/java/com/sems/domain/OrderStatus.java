package com.sems.domain;

import java.util.Arrays;

/**
 * 工单状态（设计文档 3.4.15）：待处理、处理中、待验收、已完成。
 * 维修与保养工单共用；数据库按设计文档 4.4 以中文字符串保存。
 */
public enum OrderStatus {

    PENDING("待处理"),
    PROCESSING("处理中"),
    PENDING_ACCEPTANCE("待验收"),
    COMPLETED("已完成");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static OrderStatus from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(s -> s.name().equalsIgnoreCase(trimmed) || s.label.equals(trimmed))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的工单状态：" + value));
    }
}
