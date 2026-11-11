package com.sems.web;

import com.sems.common.ApiResponse;
import com.sems.common.PageResult;
import com.sems.service.WorkOrderService;
import com.sems.web.dto.OrderDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验收工作台接口（设计文档 5.4）。
 */
@RestController
@RequestMapping("/api/v1/acceptances")
public class AcceptanceController {

    private final WorkOrderService workOrderService;

    public AcceptanceController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @GetMapping("/pending")
    public ApiResponse<PageResult<OrderDtos.OrderSummary>> pending(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(workOrderService.pendingAcceptances(page, size));
    }
}
