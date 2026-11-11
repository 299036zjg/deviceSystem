package com.sems.service;

import com.sems.entity.Acceptance;
import com.sems.entity.Equipment;
import com.sems.entity.RepairRequest;
import com.sems.entity.User;
import com.sems.entity.WorkOrder;
import com.sems.entity.WorkRecord;
import com.sems.repo.UserRepository;
import com.sems.web.dto.EquipmentDtos;
import com.sems.web.dto.FileDtos;
import com.sems.web.dto.OrderDtos;
import com.sems.web.dto.RepairDtos;
import com.sems.web.dto.UserDtos;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体到视图 DTO 的映射。费用字段按角色隐藏由调用方通过 includeCost 控制（EM008 2.a、BR12）。
 */
@Component
public class ViewMapper {

    private final UserRepository userRepository;

    public ViewMapper(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public EquipmentDtos.EquipmentView equipment(Equipment equipment) {
        if (equipment == null) {
            return null;
        }
        return new EquipmentDtos.EquipmentView(equipment.getEquipmentId(), equipment.getEquipmentCode(),
                equipment.getEquipmentName(), equipment.getCategory(), equipment.getModel(),
                equipment.getDepartment(), equipment.getLocation(), equipment.isEnabled(), equipment.getCreatedAt());
    }

    public UserDtos.UserView user(User user) {
        return new UserDtos.UserView(user.getUserId(), user.getAccount(), user.getName(), user.getDepartment(),
                user.getRole().name(), user.getRole().label(), user.isEnabled(), user.getCreatedAt());
    }

    public Map<Long, String> namesOf(Collection<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return names;
        }
        userRepository.findAllById(userIds.stream().filter(java.util.Objects::nonNull).distinct().toList())
                .forEach(u -> names.put(u.getUserId(), u.getName()));
        return names;
    }

    public OrderDtos.OrderSummary orderSummary(WorkOrder order, Equipment equipment, String ownerName,
                                              RepairRequest request, String reporterName, boolean includeCost) {
        return new OrderDtos.OrderSummary(
                order.getOrderId(),
                order.getOrderNo(),
                order.getOrderType().name(),
                order.getOrderType().label(),
                order.getEquipmentId(),
                equipment == null ? null : equipment.getEquipmentCode(),
                equipment == null ? null : equipment.getEquipmentName(),
                equipment == null ? null : equipment.getDepartment(),
                equipment == null ? null : equipment.getLocation(),
                order.getOwnerId(),
                ownerName,
                order.getTaskContent(),
                order.getPlannedDate(),
                order.getStatus().label(),
                order.getStatus().name(),
                includeCost ? order.getTotalCost() : null,
                order.getCreatedAt(),
                order.getStartedAt(),
                order.getCompletedAt(),
                order.getRequestId(),
                request == null ? null : request.getRequestNo(),
                request == null ? null : request.getFaultType(),
                request == null ? null : request.getUrgency(),
                reporterName);
    }

    public RepairDtos.RepairSummary repairSummary(RepairRequest request, Equipment equipment, String reporterName,
                                                  WorkOrder order, String ownerName, int photoCount,
                                                  boolean includeCost) {
        return new RepairDtos.RepairSummary(
                request.getRequestId(),
                request.getRequestNo(),
                request.getEquipmentId(),
                equipment == null ? null : equipment.getEquipmentCode(),
                equipment == null ? null : equipment.getEquipmentName(),
                equipment == null ? null : equipment.getDepartment(),
                equipment == null ? null : equipment.getLocation(),
                request.getReporterId(),
                reporterName,
                request.getDescription(),
                request.getFaultType(),
                request.getUrgency(),
                request.getDiscoveredAt(),
                request.getSubmittedAt(),
                request.getStatus().label(),
                request.getStatus().name(),
                request.getSource().name(),
                request.getSource().label(),
                order == null ? null : order.getOrderNo(),
                order == null ? null : order.getStatus().label(),
                ownerName,
                photoCount,
                includeCost && order != null ? order.getTotalCost() : null);
    }

    public OrderDtos.RecordView record(WorkRecord record, String operatorName,
                                       List<FileDtos.AttachmentView> attachments, boolean includeCost) {
        return new OrderDtos.RecordView(record.getRecordId(), record.getOperatorId(), operatorName,
                record.getCause(), record.getProcess(), record.getResult(), record.getReplacedParts(),
                includeCost ? record.getTotalCostSnapshot() : null, record.getSubmittedAt(), attachments);
    }

    public OrderDtos.AcceptanceView acceptance(Acceptance acceptance, String inspectorName) {
        return new OrderDtos.AcceptanceView(acceptance.getAcceptanceId(), acceptance.getInspectorId(),
                inspectorName, acceptance.getResult().name(), acceptance.getResult().label(),
                acceptance.getOpinion(), acceptance.getAcceptedAt());
    }
}
