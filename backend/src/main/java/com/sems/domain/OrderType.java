package com.sems.domain;

/**
 * 工单类型（设计文档 3.4.7、4.4）：REPAIR 维修、MAINTENANCE 保养。
 */
public enum OrderType {
    REPAIR("维修"),
    MAINTENANCE("保养");

    private final String label;

    OrderType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
