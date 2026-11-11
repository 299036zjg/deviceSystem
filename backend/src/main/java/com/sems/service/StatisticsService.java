package com.sems.service;

import com.sems.common.BizException;
import com.sems.domain.OrderStatus;
import com.sems.domain.OrderType;
import com.sems.domain.RequestStatus;
import com.sems.domain.Role;
import com.sems.repo.EquipmentRepository;
import com.sems.repo.RepairRequestRepository;
import com.sems.repo.WorkOrderRepository;
import com.sems.security.AuthContext;
import com.sems.security.Session;
import com.sems.web.dto.StatDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 查询统计与首页数字（EM008、BR05）。
 * 设备总数为全部台账；未完成工单按当前状态；完成数量与维修费用按完成日期范围统计。
 */
@Service
public class StatisticsService {

    private static final List<OrderStatus> UNFINISHED = List.of(
            OrderStatus.PENDING, OrderStatus.PROCESSING, OrderStatus.PENDING_ACCEPTANCE);

    private final EquipmentRepository equipmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final RepairRequestRepository repairRequestRepository;

    public StatisticsService(EquipmentRepository equipmentRepository,
                             WorkOrderRepository workOrderRepository,
                             RepairRequestRepository repairRequestRepository) {
        this.equipmentRepository = equipmentRepository;
        this.workOrderRepository = workOrderRepository;
        this.repairRequestRepository = repairRequestRepository;
    }

    @Transactional(readOnly = true)
    public StatDtos.Statistics statistics(LocalDate from, LocalDate to) {
        AuthContext.requireAdmin();
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (start.isAfter(end)) {
            throw BizException.param("开始日期不能晚于结束日期");
        }
        LocalDateTime fromTime = start.atStartOfDay();
        LocalDateTime toTime = end.plusDays(1).atStartOfDay();
        long doneRepair = workOrderRepository.countByOrderTypeAndStatusAndCompletedAtBetween(
                OrderType.REPAIR, OrderStatus.COMPLETED, fromTime, toTime);
        long doneMaintenance = workOrderRepository.countByOrderTypeAndStatusAndCompletedAtBetween(
                OrderType.MAINTENANCE, OrderStatus.COMPLETED, fromTime, toTime);
        BigDecimal repairCost = workOrderRepository.sumCost(OrderType.REPAIR, OrderStatus.COMPLETED,
                fromTime, toTime);
        return new StatDtos.Statistics(start, end, equipmentRepository.count(),
                workOrderRepository.countByStatusIn(UNFINISHED), doneRepair, doneMaintenance,
                repairCost == null ? BigDecimal.ZERO : repairCost);
    }

    /**
     * 首页数字：按角色返回各自可见的数量（2.3）。
     */
    @Transactional(readOnly = true)
    public StatDtos.Overview overview() {
        Session session = AuthContext.current();
        if (session.role() == Role.ADMIN) {
            return new StatDtos.Overview(Role.ADMIN.name(),
                    repairRequestRepository.countByStatus(RequestStatus.PENDING),
                    workOrderRepository.countByStatusIn(List.of(OrderStatus.PENDING_ACCEPTANCE)),
                    null, null, null, null, null,
                    equipmentRepository.count());
        }
        if (session.role() == Role.TECHNICIAN) {
            Long ownerId = session.userId();
            return new StatDtos.Overview(Role.TECHNICIAN.name(), null, null, null,
                    workOrderRepository.countByOwnerIdAndStatusIn(ownerId, UNFINISHED),
                    workOrderRepository.countByOwnerIdAndStatusIn(ownerId, List.of(OrderStatus.PENDING)),
                    workOrderRepository.countByOwnerIdAndStatusIn(ownerId, List.of(OrderStatus.PROCESSING)),
                    workOrderRepository.countByOwnerIdAndStatusIn(ownerId,
                            List.of(OrderStatus.PENDING_ACCEPTANCE)),
                    equipmentRepository.count());
        }
        return new StatDtos.Overview(Role.REPORTER.name(), null, null,
                repairRequestRepository.countByReporterIdAndStatus(session.userId(), RequestStatus.PENDING),
                null, null, null, null, equipmentRepository.countByEnabledTrue());
    }
}
