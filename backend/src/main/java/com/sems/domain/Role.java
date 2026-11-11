package com.sems.domain;

/**
 * 用户角色：管理员、报修人、维修人员（设计文档 2.1）。
 */
public enum Role {
    ADMIN("管理员"),
    REPORTER("报修人"),
    TECHNICIAN("维修人员");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * 兼容前端传入枚举名（ADMIN）或中文（管理员）两种写法。
     */
    public static Role from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("角色不能为空");
        }
        String trimmed = value.trim();
        for (Role role : values()) {
            if (role.name().equalsIgnoreCase(trimmed) || role.label.equals(trimmed)) {
                return role;
            }
        }
        throw new IllegalArgumentException("角色只能选择管理员、报修人或维修人员");
    }
}
