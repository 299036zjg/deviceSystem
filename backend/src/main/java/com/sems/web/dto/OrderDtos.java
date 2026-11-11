package com.sems.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单、保养安排、处理记录与验收 DTO（EM005、EM006、EM007、EM012）。
 */
public final class OrderDtos {

    private OrderDtos() {
    }

    public record MaintenanceRequest(
            @NotNull(message = "请选择设备") Long equipmentId,

            @NotBlank(message = "保养内容不能为空")
            @Size(max = 1000, message = "保养内容不能超过 1000 字符")
            String taskContent,

            @NotNull(message = "请选择保养日期") LocalDate plannedDate,

            @NotNull(message = "请选择负责人") Long ownerId,

            @NotBlank(message = "提交标识不能为空")
            @Size(max = 64, message = "提交标识不能超过 64 字符")
            String createToken) {
    }

    public record StartRequest(String equipmentCode) {
    }

    public record SubmitRequest(
            @Size(max = 500, message = "故障原因不能超过 500 字符") String cause,

            @Size(max = 1000, message = "维修过程不能超过 1000 字符") String process,

            @NotBlank(message = "处理结果不能为空")
            @Size(max = 1500, message = "处理结果不能超过 1500 字符")
            String result,

            @Size(max = 500, message = "更换零部件说明不能超过 500 字符") String replacedParts,

            @NotNull(message = "请填写本单总费用")
            @DecimalMin(value = "0.00", message = "费用不能小于 0")
            @Digits(integer = 8, fraction = 2, message = "费用最多 8 位整数、2 位小数，且不超过 99 999 999.99")
            BigDecimal totalCost,

            List<Long> attachmentIds,

            @NotBlank(message = "提交标识不能为空")
            @Size(max = 64, message = "提交标识不能超过 64 字符")
            String submitToken) {
    }

    public record AcceptanceRequest(
            @NotBlank(message = "请选择验收结论") String result,

            @Size(max = 500, message = "验收意见不能超过 500 字符")
            String opinion) {
    }

    public record OrderSummary(
            Long orderId,
            String orderNo,
            String orderType,
            String orderTypeLabel,
            Long equipmentId,
            String equipmentCode,
            String equipmentName,
            String department,
            String location,
            Long ownerId,
            String ownerName,
            String taskContent,
            LocalDate plannedDate,
            String status,
            String statusCode,
            BigDecimal totalCost,
            LocalDateTime createdAt,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            Long requestId,
            String requestNo,
            String faultType,
            String urgency,
            String reporterName) {
    }

    public record RecordView(
            Long recordId,
            Long operatorId,
            String operatorName,
            String cause,
            String process,
            String result,
            String replacedParts,
            BigDecimal totalCostSnapshot,
            LocalDateTime submittedAt,
            List<FileDtos.AttachmentView> attachments) {
    }

    public record AcceptanceView(
            Long acceptanceId,
            Long inspectorId,
            String inspectorName,
            String result,
            String resultLabel,
            String opinion,
            LocalDateTime acceptedAt) {
    }

    public record OrderDetail(
            OrderSummary summary,
            String description,
            LocalDateTime discoveredAt,
            String decisionReason,
            List<RecordView> records,
            List<AcceptanceView> acceptances,
            List<FileDtos.AttachmentView> repairPhotos,
            List<FileDtos.AttachmentView> workPhotos) {
    }
}
