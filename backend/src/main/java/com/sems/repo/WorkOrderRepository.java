package com.sems.repo;

import com.sems.domain.OrderStatus;
import com.sems.domain.OrderType;
import com.sems.entity.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long>, JpaSpecificationExecutor<WorkOrder> {

    Optional<WorkOrder> findByRequestId(Long requestId);

    Optional<WorkOrder> findByCreateToken(String createToken);

    boolean existsByRequestId(Long requestId);

    boolean existsByEquipmentIdAndStatusIn(Long equipmentId, Collection<OrderStatus> statuses);

    boolean existsByOwnerIdAndStatusIn(Long ownerId, Collection<OrderStatus> statuses);

    long countByStatusIn(Collection<OrderStatus> statuses);

    long countByOwnerIdAndStatusIn(Long ownerId, Collection<OrderStatus> statuses);

    long countByOrderTypeAndStatusAndCompletedAtBetween(OrderType type, OrderStatus status,
                                                        LocalDateTime from, LocalDateTime to);

    @Query("""
            select sum(o.totalCost) from WorkOrder o
             where o.orderType = :type and o.status = :status
               and o.completedAt >= :from and o.completedAt < :to
            """)
    BigDecimal sumCost(@Param("type") OrderType type,
                       @Param("status") OrderStatus status,
                       @Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to);

    List<WorkOrder> findByEquipmentIdOrderByCreatedAtDesc(Long equipmentId);

    /**
     * 开工：只有当前负责人在「待处理」状态下才能更新成功（EM005 1.a）。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WorkOrder o
               set o.status = :target, o.startedAt = :now
             where o.orderId = :orderId and o.ownerId = :ownerId and o.status = :expected
            """)
    int start(@Param("orderId") Long orderId,
              @Param("ownerId") Long ownerId,
              @Param("expected") OrderStatus expected,
              @Param("target") OrderStatus target,
              @Param("now") LocalDateTime now);

    /**
     * 提交完工：只有负责人在「处理中」状态下才能更新成功，同时更新当前总费用（BR04）。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WorkOrder o
               set o.status = :target, o.totalCost = :totalCost
             where o.orderId = :orderId and o.ownerId = :ownerId and o.status = :expected
            """)
    int submit(@Param("orderId") Long orderId,
               @Param("ownerId") Long ownerId,
               @Param("expected") OrderStatus expected,
               @Param("target") OrderStatus target,
               @Param("totalCost") BigDecimal totalCost);

    /**
     * 验收通过：只有「待验收」状态可以完成，防止重复验收（EM006 2.a）。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WorkOrder o
               set o.status = :target, o.completedAt = :now
             where o.orderId = :orderId and o.status = :expected
            """)
    int complete(@Param("orderId") Long orderId,
                 @Param("expected") OrderStatus expected,
                 @Param("target") OrderStatus target,
                 @Param("now") LocalDateTime now);

    /**
     * 验收退回：待验收回到处理中，由原负责人整改。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WorkOrder o set o.status = :target where o.orderId = :orderId and o.status = :expected")
    int returnToProcessing(@Param("orderId") Long orderId,
                           @Param("expected") OrderStatus expected,
                           @Param("target") OrderStatus target);
}
