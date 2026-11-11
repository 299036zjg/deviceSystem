package com.sems.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 故障报修 DTO（EM003、EM004、EM011）。
 */
public final class RepairDtos {

    private RepairDtos() {
    }

    public record CreateRequest(
            Long equipmentId,
            String equipmentCode,

            @NotBlank(message = "故障描述不能为空")
            @Size(max = 1000, message = "故障描述不能超过 1000 字符")
            String description,

            String faultType,
            String urgency,
            LocalDateTime discoveredAt,
            List<Long> attachmentIds,

            @NotBlank(message = "提交标识不能为空")
            @Size(max = 64, message = "提交标识不能超过 64 字符")
            String submitToken,

            String source) {
    }

    /**
     * 受理派工：负责人必填，计划日期不得早于当前日期（EM004 2.a）。
     */
    public record DecideRequest(
            Long ownerId,
            LocalDate plannedDate,
            @Size(max = 500, message = "处理意见不能超过 500 字符") String opinion) {
    }

    public record RejectRequest(
            @NotBlank(message = "驳回原因不能为空")
            @Size(max = 500, message = "驳回原因不能超过 500 字符")
            String reason) {
    }

    public record RepairSummary(
            Long requestId,
            String requestNo,
            Long equipmentId,
            String equipmentCode,
            String equipmentName,
            String department,
            String location,
            Long reporterId,
            String reporterName,
            String description,
            String faultType,
            String urgency,
            LocalDateTime discoveredAt,
            LocalDateTime submittedAt,
            String status,
            String statusCode,
            String source,
            String sourceLabel,
            String orderNo,
            String orderStatus,
            String ownerName,
            int photoCount,
            BigDecimal totalCost) {
    }

    public record RepairDetail(
            RepairSummary summary,
            String decisionReason,
            LocalDateTime decidedAt,
            String decidedByName,
            List<FileDtos.AttachmentView> attachments,
            OrderDtos.OrderSummary order,
            List<OrderDtos.RecordView> records,
            List<OrderDtos.AcceptanceView> acceptances,
            String acceptanceConclusion) {
    }
}
