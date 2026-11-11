package com.sems.web;

import com.sems.common.ApiResponse;
import com.sems.domain.AcceptanceResult;
import com.sems.domain.Dictionary;
import com.sems.domain.OrderStatus;
import com.sems.domain.OrderType;
import com.sems.domain.RequestSource;
import com.sems.domain.RequestStatus;
import com.sems.service.StatisticsService;
import com.sems.web.dto.StatDtos;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 统计、首页数字与业务字典接口（EM008）。
 */
@RestController
@RequestMapping("/api/v1")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/statistics")
    public ApiResponse<StatDtos.Statistics> statistics(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate to) {
        return ApiResponse.ok(statisticsService.statistics(from, to));
    }

    @GetMapping("/dashboard")
    public ApiResponse<StatDtos.Overview> dashboard() {
        return ApiResponse.ok(statisticsService.overview());
    }

    /**
     * 业务字典：前端与小程序共用，避免两端各自硬编码（EM011 1.b）。
     */
    @GetMapping("/dictionaries")
    public ApiResponse<Map<String, Object>> dictionaries() {
        return ApiResponse.ok(Map.of(
                "faultTypes", Dictionary.FAULT_TYPES,
                "urgencies", Dictionary.URGENCIES,
                "requestStatuses", labels(RequestStatus.values()),
                "orderStatuses", labels(OrderStatus.values()),
                "orderTypes", labels(OrderType.values()),
                "acceptanceResults", labels(AcceptanceResult.values()),
                "sources", labels(RequestSource.values())));
    }

    private List<Map<String, String>> labels(Enum<?>[] values) {
        return Arrays.stream(values).map(value -> Map.of("code", value.name(), "label", labelOf(value))).toList();
    }

    private String labelOf(Enum<?> value) {
        if (value instanceof RequestStatus v) {
            return v.label();
        }
        if (value instanceof OrderStatus v) {
            return v.label();
        }
        if (value instanceof OrderType v) {
            return v.label();
        }
        if (value instanceof AcceptanceResult v) {
            return v.label();
        }
        if (value instanceof RequestSource v) {
            return v.label();
        }
        return value.name();
    }
}
