package com.sems.service;

import com.sems.common.BizException;
import com.sems.common.ErrorCode;
import com.sems.common.PageResult;
import com.sems.common.Paging;
import com.sems.domain.OrderStatus;
import com.sems.domain.RequestStatus;
import com.sems.domain.Role;
import com.sems.entity.Equipment;
import com.sems.entity.RepairRequest;
import com.sems.entity.WorkOrder;
import com.sems.repo.EquipmentRepository;
import com.sems.repo.FileAttachmentRepository;
import com.sems.repo.RepairRequestRepository;
import com.sems.repo.WorkOrderRepository;
import com.sems.security.AuthContext;
import com.sems.security.Session;
import com.sems.web.dto.EquipmentDtos;
import com.sems.web.dto.OrderDtos;
import com.sems.web.dto.RepairDtos;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备台账与二维码（EM002、EM010、EM014）。
 */
@Service
public class EquipmentService {

    private static final List<OrderStatus> UNFINISHED = List.of(
            OrderStatus.PENDING, OrderStatus.PROCESSING, OrderStatus.PENDING_ACCEPTANCE);

    private final EquipmentRepository equipmentRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final WorkOrderRepository workOrderRepository;
    private final FileAttachmentRepository fileAttachmentRepository;
    private final QrCodeService qrCodeService;
    private final ViewMapper viewMapper;

    public EquipmentService(EquipmentRepository equipmentRepository,
                            RepairRequestRepository repairRequestRepository,
                            WorkOrderRepository workOrderRepository,
                            FileAttachmentRepository fileAttachmentRepository,
                            QrCodeService qrCodeService,
                            ViewMapper viewMapper) {
        this.equipmentRepository = equipmentRepository;
        this.repairRequestRepository = repairRequestRepository;
        this.workOrderRepository = workOrderRepository;
        this.fileAttachmentRepository = fileAttachmentRepository;
        this.qrCodeService = qrCodeService;
        this.viewMapper = viewMapper;
    }

    /**
     * 设备列表：管理员看全部，报修人只看本部门启用设备，维修人员看启用设备。
     */
    @Transactional(readOnly = true)
    public PageResult<EquipmentDtos.EquipmentView> list(String keyword, String department, Boolean enabled,
                                                        Integer page, Integer size) {
        Session session = AuthContext.current();
        Specification<Equipment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(cb.like(root.get("equipmentCode"), like),
                        cb.like(root.get("equipmentName"), like),
                        cb.like(root.get("location"), like)));
            }
            if (department != null && !department.isBlank()) {
                predicates.add(cb.equal(root.get("department"), department.trim()));
            }
            if (session.role() == Role.REPORTER) {
                predicates.add(cb.equal(root.get("department"), session.department()));
                predicates.add(cb.isTrue(root.get("enabled")));
            } else if (session.role() == Role.TECHNICIAN) {
                predicates.add(cb.isTrue(root.get("enabled")));
            } else if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return PageResult.of(equipmentRepository.findAll(spec,
                        Paging.of(page, size, Sort.by(Sort.Direction.ASC, "equipmentCode")))
                .map(viewMapper::equipment));
    }

    @Transactional(readOnly = true)
    public EquipmentDtos.EquipmentView get(Long equipmentId) {
        Equipment equipment = load(equipmentId);
        assertVisible(equipment);
        return viewMapper.equipment(equipment);
    }

    @Transactional
    public EquipmentDtos.EquipmentView create(EquipmentDtos.SaveRequest request) {
        AuthContext.requireAdmin();
        String code = request.equipmentCode().trim();
        if (equipmentRepository.existsByEquipmentCode(code)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "设备编号 " + code + " 已存在，请更换编号");
        }
        Equipment equipment = new Equipment();
        equipment.setEquipmentCode(code);
        equipment.setEquipmentName(request.equipmentName().trim());
        equipment.setCategory(trimToNull(request.category()));
        equipment.setModel(trimToNull(request.model()));
        equipment.setDepartment(request.department().trim());
        equipment.setLocation(request.location().trim());
        equipment.setEnabled(request.enabled() == null || request.enabled());
        equipment.setCreatedAt(LocalDateTime.now());
        return viewMapper.equipment(equipmentRepository.save(equipment));
    }

    /**
     * 编辑设备：设备编号一经创建不可修改（EM014 3.a、BR07）。
     */
    @Transactional
    public EquipmentDtos.EquipmentView update(Long equipmentId, EquipmentDtos.SaveRequest request) {
        AuthContext.requireAdmin();
        Equipment equipment = load(equipmentId);
        String code = request.equipmentCode().trim();
        if (!code.equals(equipment.getEquipmentCode())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "设备编号一经创建不可修改；如需变更请停用原设备并新增设备");
        }
        equipment.setEquipmentName(request.equipmentName().trim());
        equipment.setCategory(trimToNull(request.category()));
        equipment.setModel(trimToNull(request.model()));
        equipment.setDepartment(request.department().trim());
        equipment.setLocation(request.location().trim());
        return viewMapper.equipment(equipmentRepository.save(equipment));
    }

    /**
     * 停用设备：存在待受理报修或未完成工单时拒绝（EM002 3.a、BR06）。
     */
    @Transactional
    public EquipmentDtos.EquipmentView setEnabled(Long equipmentId, boolean enabled) {
        AuthContext.requireAdmin();
        Equipment equipment = load(equipmentId);
        if (equipment.isEnabled() != enabled) {
            if (!enabled) {
                if (repairRequestRepository.existsByEquipmentIdAndStatus(equipmentId, RequestStatus.PENDING)) {
                    throw new BizException(ErrorCode.STATE_CONFLICT,
                            "该设备存在待受理报修，不能停用；请先受理或驳回后再操作");
                }
                if (workOrderRepository.existsByEquipmentIdAndStatusIn(equipmentId, UNFINISHED)) {
                    throw new BizException(ErrorCode.STATE_CONFLICT,
                            "该设备存在未完成工单，不能停用；请先完成维修或保养任务");
                }
            }
            equipment.setEnabled(enabled);
            equipmentRepository.save(equipment);
        }
        return viewMapper.equipment(equipment);
    }

    /**
     * 设备履历：维修与保养单据（EM008 2）。
     */
    @Transactional(readOnly = true)
    public EquipmentDtos.HistoryView history(Long equipmentId) {
        Equipment equipment = load(equipmentId);
        assertVisible(equipment);
        Session session = AuthContext.current();
        boolean includeCost = session.role() != Role.REPORTER;

        List<RepairRequest> requests = repairRequestRepository.findByEquipmentIdOrderBySubmittedAtDesc(equipmentId);
        List<WorkOrder> orders = workOrderRepository.findByEquipmentIdOrderByCreatedAtDesc(equipmentId);
        Map<Long, WorkOrder> orderByRequest = new HashMap<>();
        orders.forEach(o -> {
            if (o.getRequestId() != null) {
                orderByRequest.put(o.getRequestId(), o);
            }
        });
        List<Long> userIds = new ArrayList<>();
        requests.forEach(r -> userIds.add(r.getReporterId()));
        orders.forEach(o -> userIds.add(o.getOwnerId()));
        Map<Long, String> names = viewMapper.namesOf(userIds);

        List<RepairDtos.RepairSummary> repairs = requests.stream()
                .map(r -> {
                    WorkOrder order = orderByRequest.get(r.getRequestId());
                    return viewMapper.repairSummary(r, equipment, names.get(r.getReporterId()),
                            order, order == null ? null : names.get(order.getOwnerId()),
                            (int) fileAttachmentRepository.countByRequestId(r.getRequestId()), includeCost);
                })
                .toList();
        List<OrderDtos.OrderSummary> orderViews = orders.stream()
                .map(o -> viewMapper.orderSummary(o, equipment, names.get(o.getOwnerId()), null, null, includeCost))
                .toList();
        return new EquipmentDtos.HistoryView(viewMapper.equipment(equipment), repairs, orderViews);
    }

    /**
     * 扫码查询设备（EM010）：按二维码内容解析设备编号，叠加部门与启用状态校验。
     */
    @Transactional(readOnly = true)
    public EquipmentDtos.QrScanView scan(String content) {
        Session session = AuthContext.current();
        Equipment equipment = resolveByQrContent(content);
        boolean departmentMatch = session.role() != Role.REPORTER
                || equipment.getDepartment().equals(session.department());
        boolean canReport = session.role() == Role.REPORTER && equipment.isEnabled() && departmentMatch;
        String message;
        if (!equipment.isEnabled()) {
            message = "设备已停用，不允许新报修";
        } else if (session.role() == Role.REPORTER && !departmentMatch) {
            message = "该设备属于" + equipment.getDepartment() + "，仅可查看位置与状态，不能提交报修";
        } else if (session.role() == Role.REPORTER) {
            message = "设备信息已识别，可以填写故障报修";
        } else {
            message = "设备信息已识别，请核对与本人工单设备是否一致";
        }
        return new EquipmentDtos.QrScanView(viewMapper.equipment(equipment), canReport, message);
    }

    /**
     * 维修人员到场核对工单设备（EM012 3.a）。
     */
    @Transactional(readOnly = true)
    public EquipmentDtos.VerifyResult verify(Long orderId, String content) {
        Session session = AuthContext.current();
        AuthContext.requireTechnician();
        WorkOrder order = workOrderRepository.findById(orderId)
                .orElseThrow(() -> BizException.notFound("工单不存在"));
        if (!order.getOwnerId().equals(session.userId())) {
            throw BizException.outOfScope("该工单不属于当前维修人员");
        }
        Equipment scanned = resolveByQrContent(content);
        Equipment orderEquipment = equipmentRepository.findById(order.getEquipmentId()).orElse(null);
        boolean matched = orderEquipment != null
                && orderEquipment.getEquipmentId().equals(scanned.getEquipmentId());
        String message = matched
                ? "设备核对一致，可以开始维修"
                : "扫码设备与工单设备不一致，请重新核对后再提交完工";
        return new EquipmentDtos.VerifyResult(matched, message, orderId,
                viewMapper.equipment(orderEquipment), viewMapper.equipment(scanned));
    }

    @Transactional(readOnly = true)
    public List<EquipmentDtos.QrItem> buildQrItems(List<Long> equipmentIds) {
        AuthContext.requireAdmin();
        if (equipmentIds == null || equipmentIds.isEmpty()) {
            throw BizException.param("请选择需要生成二维码的设备");
        }
        if (equipmentIds.size() > 50) {
            throw BizException.param("批量生成单次不超过 50 台，请分批操作");
        }
        List<Equipment> equipments = equipmentRepository.findByEquipmentIdIn(equipmentIds);
        return equipments.stream()
                .map(e -> new EquipmentDtos.QrItem(e.getEquipmentId(), e.getEquipmentCode(), e.getEquipmentName(),
                        e.getDepartment(), e.getLocation(), qrCodeService.content(e),
                        "/api/v1/equipments/" + e.getEquipmentId() + "/qrcode"))
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] qrPng(Long equipmentId) {
        AuthContext.requireAdmin();
        Equipment equipment = load(equipmentId);
        return qrCodeService.png(equipment);
    }

    /**
     * 报修提交时按设备 ID 或设备编号定位启用设备（EM011）。
     */
    @Transactional(readOnly = true)
    public Equipment loadForReporting(Long equipmentId, String equipmentCode) {
        Equipment equipment;
        if (equipmentId != null) {
            equipment = equipmentRepository.findById(equipmentId)
                    .orElseThrow(() -> BizException.notFound("设备不存在"));
        } else if (equipmentCode != null && !equipmentCode.isBlank()) {
            equipment = equipmentRepository.findByEquipmentCode(equipmentCode.trim())
                    .orElseThrow(() -> BizException.notFound("设备不存在：" + equipmentCode));
        } else {
            throw BizException.param("请选择故障设备");
        }
        if (!equipment.isEnabled()) {
            throw new BizException(ErrorCode.EQUIPMENT_DISABLED, "设备已停用，不能提交报修或安排保养");
        }
        return equipment;
    }

    @Transactional(readOnly = true)
    public Equipment load(Long equipmentId) {
        return equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> BizException.notFound("设备不存在"));
    }

    private Equipment resolveByQrContent(String content) {
        if (content == null || content.isBlank()) {
            throw BizException.param("二维码内容不能为空");
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith(qrCodeService.prefix())) {
            throw BizException.param("二维码内容不是本系统约定的格式（EQ:设备编号），请扫描设备上的二维码");
        }
        String code = trimmed.substring(qrCodeService.prefix().length()).trim();
        if (code.isEmpty()) {
            throw BizException.param("二维码内容无效，缺少设备编号");
        }
        return equipmentRepository.findByEquipmentCode(code)
                .orElseThrow(() -> BizException.notFound("设备不存在：" + code));
    }

    private void assertVisible(Equipment equipment) {
        Session session = AuthContext.current();
        if (session.role() == Role.REPORTER) {
            if (!equipment.isEnabled()) {
                throw new BizException(ErrorCode.EQUIPMENT_DISABLED, "设备已停用");
            }
            if (!equipment.getDepartment().equals(session.department())) {
                throw BizException.outOfScope("无权查看其他部门的设备");
            }
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
