package com.sems.domain;

/**
 * 图片附件业务类型（设计文档 BR08）：REPAIR 故障现场照片、WORK 维修完成照片。
 */
public enum BizType {
    REPAIR("故障现场照片"),
    WORK("维修完成照片");

    private final String label;

    BizType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
