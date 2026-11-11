package com.sems.common;

import java.util.List;

/**
 * 分页结果：items、total、page、size，见设计文档 5.1 节。
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    public static <T> PageResult<T> of(org.springframework.data.domain.Page<T> page) {
        return new PageResult<>(page.getContent(), page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    public static <T> PageResult<T> of(List<T> items, long total, int page, int size) {
        return new PageResult<>(items, total, page, size);
    }
}
