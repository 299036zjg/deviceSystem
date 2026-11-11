package com.sems.web;

import com.sems.common.ApiResponse;
import com.sems.common.PageResult;
import com.sems.service.RepairService;
import com.sems.web.dto.FileDtos;
import com.sems.web.dto.OrderDtos;
import com.sems.web.dto.RepairDtos;
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
 * 故障报修与受理派工接口（EM003、EM004、EM011）。
 */
@RestController
@RequestMapping("/api/v1/repair-requests")
public class RepairController {

    private final RepairService repairService;

    public RepairController(RepairService repairService) {
        this.repairService = repairService;
    }

    @PostMapping
    public ApiResponse<RepairDtos.RepairDetail> create(@Valid @RequestBody RepairDtos.CreateRequest request) {
        return ApiResponse.ok(repairService.create(request));
    }

    @GetMapping
    public ApiResponse<PageResult<RepairDtos.RepairSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(repairService.list(keyword, status, equipmentId, from, to, page, size));
    }

    @GetMapping("/mine")
    public ApiResponse<PageResult<RepairDtos.RepairSummary>> mine(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(repairService.mine(status, page, size));
    }

    @GetMapping("/{requestId}")
    public ApiResponse<RepairDtos.RepairDetail> detail(@PathVariable Long requestId) {
        return ApiResponse.ok(repairService.detail(requestId));
    }

    @GetMapping("/{requestId}/attachments")
    public ApiResponse<List<FileDtos.AttachmentView>> attachments(@PathVariable Long requestId) {
        return ApiResponse.ok(repairService.attachments(requestId));
    }

    @PostMapping("/{requestId}/accept")
    public ApiResponse<OrderDtos.OrderSummary> accept(@PathVariable Long requestId,
                                                      @Valid @RequestBody RepairDtos.DecideRequest request) {
        return ApiResponse.ok(repairService.accept(requestId, request));
    }

    @PostMapping("/{requestId}/reject")
    public ApiResponse<Void> reject(@PathVariable Long requestId,
                                    @Valid @RequestBody RepairDtos.RejectRequest request) {
        repairService.reject(requestId, request);
        return ApiResponse.ok();
    }
}
