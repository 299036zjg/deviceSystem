package com.sems.domain;

/**
 * 验收结论（设计文档 4.6）：PASS 通过、RETURN 退回整改。
 */
public enum AcceptanceResult {
    PASS("通过"),
    RETURN("退回");

    private final String label;

    AcceptanceResult(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
