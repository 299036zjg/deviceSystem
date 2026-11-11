package com.sems.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 单据编号生成：前缀 + 时间戳 + 三位随机数，长度不超过 32 位，唯一约束由数据库兜底。
 * 报修编号以 BX 开头，工单编号以 GD 开头。
 */
@Component
public class NoGenerator {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public String next(String prefix) {
        return prefix + LocalDateTime.now().format(FORMAT)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }
}
