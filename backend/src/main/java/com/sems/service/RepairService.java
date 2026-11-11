package com.sems.service;

import com.sems.common.BizException;
import com.sems.common.ErrorCode;
import com.sems.common.PageResult;
import com.sems.common.Paging;
import com.sems.domain.Dictionary;
import com.sems.domain.OrderStatus;
import com.sems.domain.OrderType;
import com.sems.domain.RequestSource;
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
import com.sems.web.dto.FileDtos;
import com.sems.web.dto.OrderDtos;
import com.sems.web.dto.RepairDtos;
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
 * 故障报修与受理派工（EM003、EM004、EM011）。
 */
@Service
public class RepairService {

    private final RepairRequestRepository repairRequestRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkRecordRepository workRecordRepository;
    private final AcceptanceRepository acceptanceRepository;
    private final FileAttachmentRepository fileAttachmentRepository;
    private final UserRepository userRepository;
    private final EquipmentService equipmentService;
    private final FileService fileService;
    private final ViewMapper viewMapper;
    private final NoGenerator noGenerator;

    public RepairService(RepairRequestRepository repairRequestRepository,
                         WorkOrderRepository workOrderRepository,
                         WorkRecordRepository workRecordRepository,
                         AcceptanceRepository acceptanceRepository,
                         FileAttachmentRepository fileAttachmentRepository,
                         UserRepository userRepository,
                         EquipmentService equipmentService,
                         FileService fileService,
                         ViewMapper viewMapper,
                         NoGenerator noGenerator) {
        this.repairRequestRepository = repairRequestRepository;
        this.workOrderRepository = workOrderRepository;
        this.workRecordRepository = workRecordRepository;
        this.acceptanceRepository = acceptanceRepository;
        this.fileAttachmentRepository = fileAttachmentRepository;
        this.userRepository = userRepository;
        this.equipmentService = equipmentService;
        this.fileService = fileService;
        this.viewMapper = viewMapper;
        this.noGenerator = noGenerator;
    }

    /**
     * 提交故障报修：报修人与部门由令牌确定，不接受客户端传入（EM011 4.b、BR10）。
     */
    @Transactional
    public RepairDtos.RepairDetail create(RepairDtos.CreateRequest request) {
        Session session = AuthContext.current();
        AuthContext.requireReporter();
        String token = request.submitToken().trim();

        // 一次性提交标识防重：相同报修人重试返回原单据（EM003 2.b、BR03）
        RepairRequest existing = repairRequestRepository
                .findByReporterIdAndSubmitToken(session.userId(), token).orElse(null);
        if (existing != null) {
            return detail(existing.getRequestId());
        }

        Equipment equipment = equipmentService.loadForReporting(request.equipmentId(), request.equipmentCode());
        if (!equipment.getDepartment().equals(session.department())) {
            throw BizException.outOfScope("只能对本部门设备提交报修");
        }
        String description = request.description() == null ? "" : request.description().trim();
        if (description.isEmpty() || description.length() > 1000) {
            throw new BizException(ErrorCode.PARAM_INVALID, "故障描述不能为空且不超过 1000 字符");
        }
        if (request.faultType() != null && !Dictionary.isValidFaultType(request.faultType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "故障类型不在规定取值内");
        }
        if (request.urgency() != null && !Dictionary.isValidUrgency(request.urgency())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "紧急程度不在规定取值内");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime discoveredAt = request.discoveredAt() == null ? now : request.discoveredAt();
        if (discoveredAt.isAfter(now)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发现时间不能晚于当前时间");
        }

        RepairRequest entity = new RepairRequest();
        entity.setRequestNo(noGenerator.next("BX"));
        entity.setEquipmentId(equipment.getEquipmentId());
        entity.setReporterId(session.userId());
        entity.setDescription(description);
        entity.setFaultType(request.faultType());
        entity.setUrgency(request.urgency());
        entity.setDiscoveredAt(discoveredAt);
        entity.setSubmittedAt(now);
        entity.setStatus(RequestStatus.PENDING);
        entity.setSubmitToken(token);
        entity.setSource(RequestSource.from(request.source()));
        RepairRequest saved = repairRequestRepository.save(entity);

        fileService.bindToRequest(request.attachmentIds(), saved.getRequestId());
        return detail(saved.getRequestId());
    }

    /**
     * 报修列表：管理员看全部，报修人只看本人（EM008 1.b、2.a）。
     */
    @Transactional(readOnly = true)
    public PageResult<RepairDtos.RepairSummary> list(String keyword, String status, Long equipmentId,
                                                     LocalDate from, LocalDate to, Integer page, Integer size) {
        Session session = AuthContext.current();
        if (session.role() == Role.TECHNICIAN) {
            throw BizException.forbidden("维修人员请通过工单查询处理任务");
        }
        RequestStatus statusFilter = parseStatus(status);
        Specification<RepairRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (session.role() == Role.REPORTER) {
                predicates.add(cb.equal(root.get("reporterId"), session.userId()));
            }
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            if (equipmentId != null) {
                predicates.add(cb.equal(root.get("equipmentId"), equipmentId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("submittedAt"), from.atStartOfDay()));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("submittedAt"), to.atTime(LocalTime.MAX)));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(cb.like(root.get("requestNo"), like),
                        cb.like(root.get("description"), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        org.springframework.data.domain.Page<RepairRequest> found =
                repairRequestRepository.findAll(spec, Paging.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt")));
        return PageResult.of(summaries(found.getContent()), found.getTotalElements(),
                found.getNumber() + 1, found.getSize());
    }

    @Transactional(readOnly = true)
    public PageResult<RepairDtos.RepairSummary> mine(String status, Integer page, Integer size) {
        AuthContext.requireReporter();
        return list(null, status, null, null, null, page, size);
    }

    /**
     * 报修详情：管理员或本人可见；报修人不可见费用（EM008 2.a、NFR04）。
     */
    @Transactional(readOnly = true)
    public RepairDtos.RepairDetail detail(Long requestId) {
        Session session = AuthContext.current();
        RepairRequest request = repairRequestRepository.findById(requestId)
                .orElseThrow(() -> BizException.notFound("报修单不存在"));
        boolean admin = session.role() == Role.ADMIN;
        if (!admin && !request.getReporterId().equals(session.userId())) {
            throw BizException.outOfScope("只能查看本人的报修单");
        }
        Equipment equipment = equipmentService.load(request.getEquipmentId());
        User reporter = userRepository.findById(request.getReporterId()).orElse(null);
        WorkOrder order = workOrderRepository.findByRequestId(requestId).orElse(null);
        User owner = order == null ? null : userRepository.findById(order.getOwnerId()).orElse(null);

        List<FileAttachment> photos = fileAttachmentRepository.findByRequestIdOrderBySortNoAsc(requestId);
        List<FileDtos.AttachmentView> attachments = fileService.views(photos);

        List<WorkRecord> records = workRecordRepository.findByOrderIdOrderBySubmittedAtAsc(
                order == null ? -1L : order.getOrderId());
        List<Acceptance> acceptances = acceptanceRepository.findByOrderIdOrderByAcceptedAtAsc(
                order == null ? -1L : order.getOrderId());
        Map<Long, String> names = viewMapper.namesOf(records.stream().map(WorkRecord::getOperatorId).toList());

        List<OrderDtos.RecordView> recordViews = records.stream()
                .map(r -> viewMapper.record(r, names.get(r.getOperatorId()), List.of(), admin))
                .toList();
        List<OrderDtos.AcceptanceView> acceptanceViews = acceptances.stream()
                .map(a -> viewMapper.acceptance(a,
                        userRepository.findById(a.getInspectorId()).map(User::getName).orElse(null)))
                .toList();
        String conclusion = acceptances.isEmpty() ? null
                : acceptances.get(acceptances.size() - 1).getResult().label()
                + (acceptances.get(acceptances.size() - 1).getOpinion() == null ? ""
                : "：" + acceptances.get(acceptances.size() - 1).getOpinion());

        RepairDtos.RepairSummary summary = viewMapper.repairSummary(request, equipment,
                reporter == null ? null : reporter.getName(), order, owner == null ? null : owner.getName(),
                photos.size(), admin);
        OrderDtos.OrderSummary orderSummary = order == null ? null
                : viewMapper.orderSummary(order, equipment, owner == null ? null : owner.getName(),
                request, reporter == null ? null : reporter.getName(), admin);
        User decider = request.getDecidedBy() == null ? null
                : userRepository.findById(request.getDecidedBy()).orElse(null);
        return new RepairDtos.RepairDetail(summary, request.getDecisionReason(), request.getDecidedAt(),
                decider == null ? null : decider.getName(), attachments, orderSummary,
                recordViews, acceptanceViews, conclusion);
    }

    /**
     * 受理并派工：创建维修工单与标记已受理在同一事务中完成（EM004 2.c）。
     */
    @Transactional
    public OrderDtos.OrderSummary accept(Long requestId, RepairDtos.DecideRequest request) {
        AuthContext.requireAdmin();
        RepairRequest repairRequest = repairRequestRepository.findById(requestId)
                .orElseThrow(() -> BizException.notFound("报修单不存在"));
        if (repairRequest.getStatus() != RequestStatus.PENDING) {
            throw BizException.conflict("该报修不是待受理状态，可能已被处理，请刷新后重试");
        }
        if (request.ownerId() == null) {
            throw BizException.param("请选择维修负责人");
        }
        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> BizException.param("负责人不存在或已被停用"));
        if (!owner.isEnabled() || owner.getRole() != Role.TECHNICIAN) {
            throw BizException.param("负责人必须为启用的维修人员");
        }
        LocalDate plannedDate = request.plannedDate() == null ? LocalDate.now() : request.plannedDate();
        if (plannedDate.isBefore(LocalDate.now())) {
            throw BizException.param("计划日期不能早于当前日期");
        }
        int updated = repairRequestRepository.decide(requestId, RequestStatus.PENDING, RequestStatus.ACCEPTED,
                AuthContext.userId(), LocalDateTime.now());
        if (updated == 0) {
            throw BizException.conflict("报修状态已变化，可能已被其他管理员处理");
        }

        WorkOrder order = new WorkOrder();
        order.setOrderNo(noGenerator.next("GD"));
        order.setOrderType(OrderType.REPAIR);
        order.setEquipmentId(repairRequest.getEquipmentId());
        order.setOwnerId(owner.getUserId());
        order.setRequestId(requestId);
        order.setTaskContent(repairRequest.getDescription());
        order.setPlannedDate(plannedDate);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalCost(BigDecimal.ZERO);
        order.setCreatedAt(LocalDateTime.now());
        WorkOrder saved = workOrderRepository.save(order);

        Equipment equipment = equipmentService.load(saved.getEquipmentId());
        return viewMapper.orderSummary(saved, equipment, owner.getName(), repairRequest,
                userRepository.findById(repairRequest.getReporterId()).map(User::getName).orElse(null), true);
    }

    /**
     * 驳回报修：只更新状态与原因，不创建工单（EM004 3.a）。
     */
    @Transactional
    public void reject(Long requestId, RepairDtos.RejectRequest request) {
        AuthContext.requireAdmin();
        RepairRequest repairRequest = repairRequestRepository.findById(requestId)
                .orElseThrow(() -> BizException.notFound("报修单不存在"));
        if (repairRequest.getStatus() != RequestStatus.PENDING) {
            throw BizException.conflict("该报修不是待受理状态，可能已被处理，请刷新后重试");
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty()) {
            throw BizException.param("驳回原因不能为空");
        }
        int updated = repairRequestRepository.decideWithReason(requestId, RequestStatus.PENDING,
                RequestStatus.REJECTED, reason, AuthContext.userId(), LocalDateTime.now());
        if (updated == 0) {
            throw BizException.conflict("报修状态已变化，可能已被其他管理员处理");
        }
    }

    @Transactional(readOnly = true)
    public List<FileDtos.AttachmentView> attachments(Long requestId) {
        Session session = AuthContext.current();
        RepairRequest request = repairRequestRepository.findById(requestId)
                .orElseThrow(() -> BizException.notFound("报修单不存在"));
        if (session.role() != Role.ADMIN && !request.getReporterId().equals(session.userId())) {
            throw BizException.outOfScope("只能查看本人报修的照片");
        }
        return fileService.viewsOfRequest(requestId);
    }

    private List<RepairDtos.RepairSummary> summaries(List<RepairRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        Session session = AuthContext.current();
        boolean admin = session.role() == Role.ADMIN;
        List<Long> equipmentIds = requests.stream().map(RepairRequest::getEquipmentId).distinct().toList();
        Map<Long, Equipment> equipmentMap = new HashMap<>();
        equipmentIds.forEach(id -> equipmentMap.put(id, equipmentService.load(id)));
        Map<Long, WorkOrder> orderMap = new HashMap<>();
        Map<Long, Integer> photoCount = new HashMap<>();
        for (RepairRequest request : requests) {
            workOrderRepository.findByRequestId(request.getRequestId())
                    .ifPresent(o -> orderMap.put(request.getRequestId(), o));
            photoCount.put(request.getRequestId(),
                    (int) fileAttachmentRepository.countByRequestId(request.getRequestId()));
        }
        List<Long> userIds = new ArrayList<>();
        requests.forEach(r -> userIds.add(r.getReporterId()));
        orderMap.values().forEach(o -> userIds.add(o.getOwnerId()));
        Map<Long, String> names = viewMapper.namesOf(userIds);

        List<RepairDtos.RepairSummary> result = new ArrayList<>(requests.size());
        for (RepairRequest request : requests) {
            WorkOrder order = orderMap.get(request.getRequestId());
            result.add(viewMapper.repairSummary(request, equipmentMap.get(request.getEquipmentId()),
                    names.get(request.getReporterId()), order,
                    order == null ? null : names.get(order.getOwnerId()),
                    photoCount.get(request.getRequestId()), admin));
        }
        return result;
    }

    private RequestStatus parseStatus(String status) {
        try {
            return RequestStatus.from(status);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, ex.getMessage());
        }
    }
}
