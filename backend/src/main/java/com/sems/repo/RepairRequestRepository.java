package com.sems.repo;

import com.sems.domain.RequestStatus;
import com.sems.entity.RepairRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RepairRequestRepository extends JpaRepository<RepairRequest, Long>, JpaSpecificationExecutor<RepairRequest> {

    Optional<RepairRequest> findByReporterIdAndSubmitToken(Long reporterId, String submitToken);

    boolean existsByReporterIdAndSubmitToken(Long reporterId, String submitToken);

    long countByStatus(RequestStatus status);

    long countByReporterIdAndStatus(Long reporterId, RequestStatus status);

    boolean existsByEquipmentIdAndStatus(Long equipmentId, RequestStatus status);

    boolean existsByEquipmentIdAndStatusIn(Long equipmentId, Collection<RequestStatus> statuses);

    List<RepairRequest> findByEquipmentIdOrderBySubmittedAtDesc(Long equipmentId);

    /**
     * 受理：以「当前状态 = 待受理」为条件更新，防止重复受理（BR02、EM004 2.b）。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RepairRequest r
               set r.status = :target, r.decidedBy = :adminId, r.decidedAt = :now
             where r.requestId = :requestId and r.status = :expected
            """)
    int decide(@Param("requestId") Long requestId,
               @Param("expected") RequestStatus expected,
               @Param("target") RequestStatus target,
               @Param("adminId") Long adminId,
               @Param("now") LocalDateTime now);

    /**
     * 驳回：同时写入驳回原因。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RepairRequest r
               set r.status = :target, r.decisionReason = :reason,
                   r.decidedBy = :adminId, r.decidedAt = :now
             where r.requestId = :requestId and r.status = :expected
            """)
    int decideWithReason(@Param("requestId") Long requestId,
                         @Param("expected") RequestStatus expected,
                         @Param("target") RequestStatus target,
                         @Param("reason") String reason,
                         @Param("adminId") Long adminId,
                         @Param("now") LocalDateTime now);

    /**
     * 维修工单验收通过时同步报修为已完成（EM006 2.b）。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RepairRequest r set r.status = :target where r.requestId = :requestId and r.status = :expected")
    int complete(@Param("requestId") Long requestId,
                 @Param("expected") RequestStatus expected,
                 @Param("target") RequestStatus target);
}
