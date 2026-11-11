package com.sems.web;

import com.sems.common.ApiResponse;
import com.sems.common.PageResult;
import com.sems.service.WorkOrderService;
import com.sems.web.dto.FileDtos;
import com.sems.web.dto.OrderDtos;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 工单、保养安排、开工、完工与验收接口（EM005、EM006、EM007、EM012）。
 */
@RestController
@RequestMapping("/api/v1/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @GetMapping
    public ApiResponse<PageResult<OrderDtos.OrderSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(workOrderService.list(keyword, status, type, ownerId, equipmentId,
                from, to, page, size));
    }

    @GetMapping("/mine")
    public ApiResponse<PageResult<OrderDtos.OrderSummary>> mine(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(workOrderService.mine(status, page, size));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDtos.OrderDetail> detail(@PathVariable Long orderId) {
        return ApiResponse.ok(workOrderService.detail(orderId));
    }

    @GetMapping("/{orderId}/attachments")
    public ApiResponse<List<FileDtos.AttachmentView>> attachments(@PathVariable Long orderId) {
        return ApiResponse.ok(workOrderService.attachments(orderId));
    }

    /**
     * 安排保养任务：管理员直接创建保养工单（EM007）。
     */
    @PostMapping
    public ApiResponse<OrderDtos.OrderDetail> createMaintenance(
            @Valid @RequestBody OrderDtos.MaintenanceRequest request) {
        return ApiResponse.ok(workOrderService.createMaintenance(request));
    }

    @PostMapping("/{orderId}/start")
    public ApiResponse<OrderDtos.OrderDetail> start(@PathVariable Long orderId,
                                                    @RequestBody(required = false) OrderDtos.StartRequest request) {
        return ApiResponse.ok(workOrderService.start(orderId, request));
    }

    @PostMapping("/{orderId}/submit")
    public ApiResponse<OrderDtos.OrderDetail> submit(@PathVariable Long orderId,
                                                     @Valid @RequestBody OrderDtos.SubmitRequest request) {
        return ApiResponse.ok(workOrderService.submit(orderId, request));
    }

    /**
     * 验收：PASS 通过 / RETURN 退回（EM006）。
     */
    @PostMapping("/{orderId}/acceptance")
    public ApiResponse<OrderDtos.OrderDetail> acceptance(@PathVariable Long orderId,
                                                         @Valid @RequestBody OrderDtos.AcceptanceRequest request) {
        return ApiResponse.ok(workOrderService.acceptance(orderId, request));
    }
}
