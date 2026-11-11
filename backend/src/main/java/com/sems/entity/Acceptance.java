package com.sems.entity;

import com.sems.domain.AcceptanceResult;
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

import java.time.LocalDateTime;

/**
 * 验收记录（设计文档 4.6）：每次验收保留结论、意见、时间与验收人，不得改删。
 */
@Entity
@Table(name = "acceptance")
@Getter
@Setter
@NoArgsConstructor
public class Acceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "acceptance_id")
    private Long acceptanceId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "inspector_id", nullable = false)
    private Long inspectorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AcceptanceResult result;

    @Column(length = 500)
    private String opinion;

    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt = LocalDateTime.now();
}
