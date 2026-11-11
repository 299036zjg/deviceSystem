package com.sems.domain;

import java.util.List;

/**
 * 业务字典（设计文档 EM011 1.b：故障类型、紧急程度须在规定取值内）。
 * 前端与小程序通过 GET /api/v1/dictionaries 获取，避免两端各自硬编码。
 */
public final class Dictionary {

    private Dictionary() {
    }

    public static final List<String> FAULT_TYPES = List.of(
            "机械故障", "电气故障", "控制系统故障", "液压气动故障", "其他");

    public static final List<String> URGENCIES = List.of("一般", "较高", "紧急");

    public static boolean isValidFaultType(String value) {
        return value != null && FAULT_TYPES.contains(value.trim());
    }

    public static boolean isValidUrgency(String value) {
        return value != null && URGENCIES.contains(value.trim());
    }
}
