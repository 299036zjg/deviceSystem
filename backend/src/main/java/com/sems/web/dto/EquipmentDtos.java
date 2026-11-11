package com.sems.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备台账与设备二维码 DTO（EM002、EM010、EM014）。
 */
public final class EquipmentDtos {

    private EquipmentDtos() {
    }

    public record SaveRequest(
            @NotBlank(message = "设备编号不能为空")
            @Size(max = 32, message = "设备编号不能超过 32 字符")
            String equipmentCode,

            @NotBlank(message = "设备名称不能为空")
            @Size(max = 100, message = "设备名称不能超过 100 字符")
            String equipmentName,

            @Size(max = 50, message = "分类不能超过 50 字符")
            String category,

            @Size(max = 100, message = "型号不能超过 100 字符")
            String model,

            @NotBlank(message = "所属部门不能为空")
            @Size(max = 50, message = "部门不能超过 50 字符")
            String department,

            @NotBlank(message = "使用位置不能为空")
            @Size(max = 100, message = "使用位置不能超过 100 字符")
            String location,

            Boolean enabled) {
    }

    public record EquipmentView(
            Long equipmentId,
            String equipmentCode,
            String equipmentName,
            String category,
            String model,
            String department,
            String location,
            boolean enabled,
            LocalDateTime createdAt) {
    }

    public record QrItem(
            Long equipmentId,
            String equipmentCode,
            String equipmentName,
            String department,
            String location,
            String content,
            String imageUrl) {
    }

    public record QrBatchRequest(
            @NotEmpty(message = "请选择需要生成二维码的设备")
            @Size(max = 50, message = "批量生成单次不超过 50 台")
            List<Long> equipmentIds) {
    }

    /**
     * 扫码结果：设备视图 + 是否可报修（EM010）。
     */
    public record QrScanView(
            EquipmentView equipment,
            boolean canReport,
            String message) {
    }

    /**
     * 维修人员到场核对结果（EM012 3.a）。
     */
    public record VerifyResult(
            boolean matched,
            String message,
            Long orderId,
            EquipmentView orderEquipment,
            EquipmentView scannedEquipment) {
    }

    /**
     * 设备履历（EM008 2）：维修与保养单据。
     */
    public record HistoryView(
            EquipmentView equipment,
            List<RepairDtos.RepairSummary> repairs,
            List<OrderDtos.OrderSummary> orders) {
    }
}
