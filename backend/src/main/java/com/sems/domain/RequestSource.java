package com.sems.domain;

/**
 * 报修提交终端（设计文档 4.3 source）：WEB 或 MINI，仅用于追溯统计。
 */
public enum RequestSource {
    WEB("Web 后台"),
    MINI("微信小程序");

    private final String label;

    RequestSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static RequestSource from(String value) {
        if (value == null || value.isBlank()) {
            return WEB;
        }
        return "MINI".equalsIgnoreCase(value.trim()) ? MINI : WEB;
    }
}
