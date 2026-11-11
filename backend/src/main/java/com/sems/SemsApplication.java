package com.sems;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 设备维修保养系统启动类。
 * 软件工程课程设计第 16 组。
 */
@SpringBootApplication
@EnableScheduling
public class SemsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SemsApplication.class, args);
    }
}
