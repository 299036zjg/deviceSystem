package com.sems.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 分页参数：page 从 1 开始，size 默认 20、最大 50（设计文档 5.1）。
 */
public final class Paging {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    private Paging() {
    }

    public static Pageable of(Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(p - 1, s);
    }

    public static Pageable of(Integer page, Integer size, Sort sort) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(p - 1, s, sort);
    }
}
