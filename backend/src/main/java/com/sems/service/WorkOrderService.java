package com.sems.service;

import com.sems.common.BizException;
import com.sems.common.ErrorCode;
import com.sems.common.PageResult;
import com.sems.common.Paging;
import com.sems.domain.AcceptanceResult;
import com.sems.domain.OrderStatus;
import com.sems.domain.OrderType;
import com.sems.domain.RequestStatus;
import com.sems.domain.Role;
import com.sems.entity.Acceptance;
import com.sems.entity.Equipment;
import com.sems.entity.FileAttachment;
import com.sems.entity.RepairRequest;
import com.sems.entity.User;
import com.sems.entity.WorkOrder;
import com.sems.entity.WorkRecord;
import com.sems.repo.AcceptanceRepository;
import com.sems.repo.FileAttachmentRepository;
import com.sems.repo.RepairRequestRepository;
import com.sems.repo.UserRepository;
import com.sems.repo.WorkOrderRepository;
import com.sems.repo.WorkRecordRepository;
import com.sems.security.AuthContext;
import com.sems.security.Session;
import com.sems.web.dto.EquipmentDtos;
import com.sems.web.dto.FileDtos;
import com.sems.web.dto.OrderDtos;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工单、保养安排、开工、完工与验收（EM005、EM006、EM007、EM012）。
 * 维修与保养共用同一状态机（3.4.15）：待处理 → 处理中 → 待验收 → 已完成。
 */
@Service
public class WorkOrderService {

    private static final BigDecimal MAX_COST = new BigDecimal("99999999.99");

    private final WorkOrderRepository workOrderRepository;
    private final WorkRecordRepository workRecordRepository;
    private final AcceptanceRepository acceptanceRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final FileAttachmentRepository fileAttachmentRepository;
    private final UserRepository userRepository;
    private final EquipmentService equipmentService;
    private final FileService fileService;
    private final ViewMapper viewMapper;
    private final NoGenerator noGenerator;

    public WorkOrderService(WorkOrderRepository workOrderRepository,
                            WorkRecordRepository workRecordRepository,
                            AcceptanceRepository acceptanceRepository,
                            RepairRequestRepository repairRequestRepository,
                            FileAttachmentRepository fileAttachmentRepository,
                            UserRepository userRepository,
                            EquipmentService equipmentService,
                            FileService fileService,
                            ViewMapper viewMapper,
                            NoGenerator noGenerator) {
        this.workOrderRepository = workOrderRepository;
        this.workRecordRepository = workRecordRepository;
        this.acceptanceRepository = acceptanceRepository;
        this.repairRequestRepository = repairRequestRepository;
        this.fileAttachmentRepository = fileAttachmentRepository;
        this.userRepository = userRepository;
        this.equipmentService = equipmentService;
        this.fileService = fileService;
        this.viewMapper = viewMapper;
        this.noGenerator = noGenerator;
    }

    /**
     * 安排保养任务：管理员直接创建保养工单，不经过报修（EM007）。
     */
    @Transactional
    public OrderDtos.OrderDetail createMaintenance(OrderDtos.MaintenanceRequest request) {
        AuthContext.requireAdmin();
        String token = request.createToken().trim();
        WorkOrder existing = workOrderRepository.findByCreateToken(token).orElse(null);
        if (existing != null) {
            return detail(existing.getOrderId());
        }
        Equipment equipment = equipmentService.loadForReporting(request.equipmentId(), null);
        if (request.plannedDate().isBefore(LocalDate.now())) {
            throw BizException.param("保养日期不能早于当前日期");
        }
        String content = request.taskContent() == null ? "" : request.taskContent().trim();
        if (content.isEmpty() || content.length() > 1000) {
            throw BizException.param("保养内容不能为空且不超过 1000 字符");
        }
        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> BizException.param("负责人不存在或已被停用"));
        if (!owner.isEnabled() || owner.getRole() != Role.TECHNICIAN) {
            throw BizException.param("负责人必须为启用的维修人员");
        }
        WorkOrder order = new WorkOrder();
        order.setOrderNo(noGenerator.next("GD"));
        order.setOrderType(OrderType.MAINTENANCE);
        order.setEquipmentId(equipment.getEquipmentId());
        order.setOwnerId(owner.getUserId());
        order.setRequestId(null);
        order.setTaskContent(content);
        order.setPlannedDate(request.plannedDate());
        order.setStatus(OrderStatus.PENDING);
        order.setTotalCost(BigDecimal.ZERO);
        order.setCreatedAt(LocalDateTime.now());
        order.setCreateToken(token);
        WorkOrder saved = workOrderRepository.save(order);
        return detail(saved.getOrderId());
    }

    /**
     * 工单列表：管理员看全部，维修人员只看本人任务（EM008 1.b）。
     */
    @Transactional(readOnly = true)
    public PageResult<OrderDtos.OrderSummary> list(String keyword, String status, String type, Long ownerId,
                                                   Long equipmentId, LocalDate from, LocalDate to,
                                                   Integer page, Integer size) {
        Session session = AuthContext.current();
        if (session.role() == Role.REPORTER) {
            throw BizException.forbidden("报修人请通过我的报修查询进度");
        }
        OrderStatus statusFilter = parseStatus(status);
        OrderType typeFilter = parseType(type);
        Specification<WorkOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (session.role() == Role.TECHNICIAN) {
                predicates.add(cb.equal(root.get("ownerId"), session.userId()));
            } else if (ownerId != null) {
                predicates.add(cb.equal(root.get("ownerId"), ownerId));
            }
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            if (typeFilter != null) {
                predicates.add(cb.equal(root.get("orderType"), typeFilter));
            }
            if (equipmentId != null) {
                predicates.add(cb.equal(root.get("equipmentId"), equipmentId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to.atTime(LocalTime.MAX)));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(cb.like(root.get("orderNo"), like),
                        cb.like(root.get("taskContent"), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        org.springframework.data.domain.Page<WorkOrder> found = workOrderRepository
                .findAll(spec, Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResult.of(summaries(found.getContent()), found.getTotalElements(),
                found.getNumber() + 1, found.getSize());
    }

    @Transactional(readOnly = true)
    public PageResult<OrderDtos.OrderSummary> mine(String status, Integer page, Integer size) {
        AuthContext.requireTechnician();
        return list(null, status, null, null, null, null, null, page, size);
    }

    /**
     * 待验收工单（管理员验收工作台）。
     */
    @Transactional(readOnly = true)
    public PageResult<OrderDtos.OrderSummary> pendingAcceptances(Integer page, Integer size) {
        AuthContext.requireAdmin();
        return list(null, OrderStatus.PENDING_ACCEPTANCE.name(), null, null, null, null, null, page, size);
    }

    /**
     * 工单详情：管理员或本单负责人可见（EM013 1.a）。
     */
    @Transactional(readOnly = true)
    public OrderDtos.OrderDetail detail(Long orderId) {
        Session session = AuthContext.current();
        WorkOrder order = loadOrder(orderId);
        boolean admin = session.role() == Role.ADMIN;
        boolean owner = order.getOwnerId().equals(session.userId());
        if (!admin && !owner) {
            throw BizException.outOfScope("只能查看本人的工单");
        }
        return buildDetail(order, admin || owner);
    }

    /**
     * 开工：负责人将待处理工单转为处理中并记录开始时间（EM005 1）。
     */
    @Transactional
    public OrderDtos.OrderDetail start(Long orderId, OrderDtos.StartRequest request) {
        Session session = AuthContext.current();
        AuthContext.requireTechnician();
        WorkOrder order = loadOrder(orderId);
        if (!order.getOwnerId().equals(session.userId())) {
            throw BizException.outOfScope("该工单不属于当前维修人员");
        }
        if (request != null && request.equipmentCode() != null && !request.equipmentCode().isBlank()) {
            EquipmentDtos.VerifyResult verify = equipmentService.verify(orderId, "EQ:" + request.equipmentCode().trim());
            if (!verify.matched()) {
                throw BizException.param("扫码设备与工单设备不一致，请重新核对");
            }
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw BizException.illegalTransition("工单当前状态为「" + order.getStatus().label() + "」，不能开工");
        }
        int updated = workOrderRepository.start(orderId, session.userId(), OrderStatus.PENDING,
                OrderStatus.PROCESSING, LocalDateTime.now());
        if (updated == 0) {
            throw BizException.conflict("工单状态已变化，请刷新后重试");
        }
        return buildDetail(loadOrder(orderId), true);
    }

    /**
     * 提交完工：追加处理记录、更新总费用并转为待验收，三者在同一事务内完成（EM005 3.a、BR09）。
     */
    @Transactional
    public OrderDtos.OrderDetail submit(Long orderId, OrderDtos.SubmitRequest request) {
        Session session = AuthContext.current();
        AuthContext.requireTechnician();
        WorkOrder order = loadOrder(orderId);
        if (!order.getOwnerId().equals(session.userId())) {
            throw BizException.outOfScope("该工单不属于当前维修人员");
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            throw BizException.illegalTransition("请先开始维修，再提交完工");
        }
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw BizException.illegalTransition("工单已完成，不能重复提交");
        }
        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw BizException.conflict("工单当前状态为「" + order.getStatus().label() + "」，不能提交完工");
        }
        String result = request.result() == null ? "" : request.result().trim();
        if (result.isEmpty() || result.length() > 1500) {
            throw BizException.param("处理结果不能为空且不超过 1500 字符");
        }
        BigDecimal cost = request.totalCost();
        if (cost == null) {
            throw BizException.param("请填写本单总费用");
        }
        if (cost.signum() < 0 || cost.compareTo(MAX_COST) > 0) {
            throw BizException.param("费用不能小于 0，且不超过 99 999 999.99 元");
        }
        if (cost.scale() > 2) {
            throw BizException.param("费用最多保留两位小数");
        }
        List<Long> attachmentIds = request.attachmentIds() == null ? List.of() : request.attachmentIds();
        if (attachmentIds.isEmpty()) {
            throw BizException.param("请上传至少 1 张维修完成照片后再提交完工");
        }
        if (attachmentIds.size() > 6) {
            throw new BizException(ErrorCode.FILE_LIMIT, "单次最多上传 6 张维修完成照片");
        }

        int updated = workOrderRepository.submit(orderId, session.userId(), OrderStatus.PROCESSING,
                OrderStatus.PENDING_ACCEPTANCE, cost);
        if (updated == 0) {
            throw BizException.conflict("工单状态已变化或已提交，请刷新后重试");
        }
        WorkRecord record = new WorkRecord();
        record.setOrderId(orderId);
        record.setOperatorId(session.userId());
        record.setCause(trimToNull(request.cause()));
        record.setProcess(trimToNull(request.process()));
        record.setResult(result);
        record.setReplacedParts(trimToNull(request.replacedParts()));
        record.setTotalCostSnapshot(cost);
        record.setSubmittedAt(LocalDateTime.now());
        WorkRecord saved = workRecordRepository.save(record);
        fileService.bindToRecord(attachmentIds, saved.getRecordId());
        return buildDetail(loadOrder(orderId), true);
    }

    /**
     * 验收：通过则完成工单并同步报修；退回则回到处理中并要求填写原因（EM006）。
     */
    @Transactional
    public OrderDtos.OrderDetail acceptance(Long orderId, OrderDtos.AcceptanceRequest request) {
        AuthContext.requireAdmin();
        WorkOrder order = loadOrder(orderId);
        AcceptanceResult result;
        try {
            result = AcceptanceResult.valueOf(request.result().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BizException.param("验收结论只能为 PASS（通过）或 RETURN（退回）");
        }
        if (order.getStatus() != OrderStatus.PENDING_ACCEPTANCE) {
            throw BizException.illegalTransition("工单当前状态为「" + order.getStatus().label()
                    + "」，只有待验收工单可以验收");
        }
        String opinion = trimToNull(request.opinion());
        if (result == AcceptanceResult.RETURN) {
            if (opinion == null) {
                throw BizException.param("退回时必须填写退回原因");
            }
            int updated = workOrderRepository.returnToProcessing(orderId, OrderStatus.PENDING_ACCEPTANCE,
                    OrderStatus.PROCESSING);
            if (updated == 0) {
                throw BizException.conflict("工单状态已变化，请刷新后重试");
            }
            saveAcceptance(orderId, AcceptanceResult.RETURN, opinion);
            return buildDetail(loadOrder(orderId), true);
        }

        int updated = workOrderRepository.complete(orderId, OrderStatus.PENDING_ACCEPTANCE,
                OrderStatus.COMPLETED, LocalDateTime.now());
        if (updated == 0) {
            throw BizException.conflict("工单状态已变化，可能已被其他管理员验收");
        }
        saveAcceptance(orderId, AcceptanceResult.PASS, opinion);
        if (order.getRequestId() != null) {
            int synced = repairRequestRepository.complete(order.getRequestId(), RequestStatus.ACCEPTED,
                    RequestStatus.COMPLETED);
            if (synced == 0) {
                throw BizException.conflict("关联报修单状态异常，验收已回滚");
            }
        }
        return buildDetail(loadOrder(orderId), true);
    }

    @Transactional(readOnly = true)
    public List<FileDtos.AttachmentView> attachments(Long orderId) {
        Session session = AuthContext.current();
        WorkOrder order = loadOrder(orderId);
        if (session.role() != Role.ADMIN && !order.getOwnerId().equals(session.userId())) {
            throw BizException.outOfScope("只能查看本人工单的照片");
        }
        List<FileDtos.AttachmentView> views = new ArrayList<>();
        if (order.getRequestId() != null) {
            views.addAll(fileService.viewsOfRequest(order.getRequestId()));
        }
        views.addAll(fileService.viewsOfWorkRecords(workRecordRepository.findByOrderIdOrderBySubmittedAtAsc(orderId)));
        return views;
    }

    private void saveAcceptance(Long orderId, AcceptanceResult result, String opinion) {
        Acceptance acceptance = new Acceptance();
        acceptance.setOrderId(orderId);
        acceptance.setInspectorId(AuthContext.userId());
        acceptance.setResult(result);
        acceptance.setOpinion(opinion);
        acceptance.setAcceptedAt(LocalDateTime.now());
        acceptanceRepository.save(acceptance);
    }

    private OrderDtos.OrderDetail buildDetail(WorkOrder order, boolean includeCost) {
        Equipment equipment = equipmentService.load(order.getEquipmentId());
        User owner = userRepository.findById(order.getOwnerId()).orElse(null);
        RepairRequest repairRequest = order.getRequestId() == null ? null
                : repairRequestRepository.findById(order.getRequestId()).orElse(null);
        User reporter = repairRequest == null ? null
                : userRepository.findById(repairRequest.getReporterId()).orElse(null);

        List<WorkRecord> records = workRecordRepository.findByOrderIdOrderBySubmittedAtAsc(order.getOrderId());
        List<Acceptance> acceptances = acceptanceRepository.findByOrderIdOrderByAcceptedAtAsc(order.getOrderId());
        Map<Long, List<FileDtos.AttachmentView>> photosByRecord = new HashMap<>();
        if (!records.isEmpty()) {
            List<Long> recordIds = records.stream().map(WorkRecord::getRecordId).toList();
            List<FileAttachment> all = fileAttachmentRepository
                    .findByRecordIdInOrderByRecordIdAscSortNoAsc(recordIds);
            Map<Long, List<FileAttachment>> grouped = new HashMap<>();
            all.forEach(a -> grouped.computeIfAbsent(a.getRecordId(), k -> new ArrayList<>()).add(a));
            grouped.forEach((recordId, list) -> photosByRecord.put(recordId, fileService.views(list)));
        }
        Map<Long, String> names = viewMapper.namesOf(records.stream().map(WorkRecord::getOperatorId).toList());
        List<OrderDtos.RecordView> recordViews = records.stream()
                .map(r -> viewMapper.record(r, names.get(r.getOperatorId()),
                        photosByRecord.getOrDefault(r.getRecordId(), List.of()), includeCost))
                .toList();
        List<OrderDtos.AcceptanceView> acceptanceViews = acceptances.stream()
                .map(a -> viewMapper.acceptance(a,
                        userRepository.findById(a.getInspectorId()).map(User::getName).orElse(null)))
                .toList();
        List<FileDtos.AttachmentView> repairPhotos = repairRequest == null ? List.of()
                : fileService.viewsOfRequest(repairRequest.getRequestId());
        List<FileDtos.AttachmentView> workPhotos = recordViews.stream()
                .flatMap(r -> r.attachments().stream()).toList();

        OrderDtos.OrderSummary summary = viewMapper.orderSummary(order, equipment,
                owner == null ? null : owner.getName(), repairRequest,
                reporter == null ? null : reporter.getName(), includeCost);
        return new OrderDtos.OrderDetail(summary,
                repairRequest == null ? order.getTaskContent() : repairRequest.getDescription(),
                repairRequest == null ? null : repairRequest.getDiscoveredAt(),
                repairRequest == null ? null : repairRequest.getDecisionReason(),
                recordViews, acceptanceViews, repairPhotos, workPhotos);
    }

    private List<OrderDtos.OrderSummary> summaries(List<WorkOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Session session = AuthContext.current();
        boolean admin = session.role() == Role.ADMIN;
        Map<Long, Equipment> equipmentMap = new HashMap<>();
        orders.stream().map(WorkOrder::getEquipmentId).distinct()
                .forEach(id -> equipmentMap.put(id, equipmentService.load(id)));
        List<Long> userIds = new ArrayList<>(orders.stream().map(WorkOrder::getOwnerId).toList());
        Map<Long, RepairRequest> requestMap = new HashMap<>();
        orders.stream().map(WorkOrder::getRequestId).filter(java.util.Objects::nonNull).distinct()
                .forEach(id -> repairRequestRepository.findById(id).ifPresent(r -> requestMap.put(id, r)));
        requestMap.values().forEach(r -> userIds.add(r.getReporterId()));
        Map<Long, String> names = viewMapper.namesOf(userIds);
        return orders.stream()
                .map(o -> {
                    RepairRequest request = requestMap.get(o.getRequestId());
                    boolean costVisible = admin || o.getOwnerId().equals(session.userId());
                    return viewMapper.orderSummary(o, equipmentMap.get(o.getEquipmentId()),
                            names.get(o.getOwnerId()), request,
                            request == null ? null : names.get(request.getReporterId()), costVisible);
                })
                .toList();
    }

    private WorkOrder loadOrder(Long orderId) {
        return workOrderRepository.findById(orderId)
                .orElseThrow(() -> BizException.notFound("工单不存在"));
    }

    private OrderStatus parseStatus(String status) {
        try {
            return OrderStatus.from(status);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, ex.getMessage());
        }
    }

    private OrderType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return OrderType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BizException.param("工单类型只能为 REPAIR 或 MAINTENANCE");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
