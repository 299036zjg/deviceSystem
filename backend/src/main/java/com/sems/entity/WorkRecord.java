package com.sems.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 处理记录（设计文档 4.5）：只在提交完工时追加，不修改、不删除。
 * total_cost_snapshot 为提交时的本单总费用快照，不累加（BR04）。
 */
@Entity
@Table(name = "work_record")
@Getter
@Setter
@NoArgsConstructor
public class WorkRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_id")
    private Long recordId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(length = 500)
    private String cause;

    @Column(length = 1000)
    private String process;

    @Column(nullable = false, length = 1500)
    private String result;

    @Column(name = "replaced_parts", length = 500)
    private String replacedParts;

    @Column(name = "total_cost_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCostSnapshot = BigDecimal.ZERO;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();
}
