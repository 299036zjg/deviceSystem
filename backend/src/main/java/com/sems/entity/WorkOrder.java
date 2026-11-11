package com.sems.entity;

import com.sems.domain.OrderStatus;
import com.sems.domain.OrderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工单（设计文档 4.4）。维修工单由报修受理产生，保养工单由管理员直接安排。
 */
@Entity
@Table(name = "work_order")
@Getter
@Setter
@NoArgsConstructor
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_no", nullable = false, length = 32, unique = true)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 12)
    private OrderType orderType;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "request_id", unique = true)
    private Long requestId;

    @Column(name = "task_content", nullable = false, length = 1000)
    private String taskContent;

    @Column(name = "planned_date", nullable = false)
    private LocalDate plannedDate;

    @Column(nullable = false, length = 16)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "total_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "create_token", length = 64, unique = true)
    private String createToken;
}
