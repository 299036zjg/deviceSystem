package com.sems.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 统计与首页数据 DTO（EM008）。
 */
public final class StatDtos {

    private StatDtos() {
    }

    public record Statistics(
            LocalDate from,
            LocalDate to,
            long equipmentCount,
            long unfinishedOrderCount,
            long doneRepairCount,
            long doneMaintenanceCount,
            BigDecimal repairCostTotal) {
    }

    /**
     * 首页数字：管理员看待受理与待验收，维修人员看本人未完成工单，报修人看本人报修进度。
     */
    public record Overview(
            String role,
            Long pendingRepairCount,
            Long pendingAcceptanceCount,
            Long myPendingRepairCount,
            Long myUnfinishedOrderCount,
            Long myPendingOrderCount,
            Long myProcessingOrderCount,
            Long myPendingAcceptanceOrderCount,
            Long equipmentCount) {
    }
}
