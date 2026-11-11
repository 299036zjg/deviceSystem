package com.sems.entity;

import com.sems.domain.RequestSource;
import com.sems.domain.RequestStatus;
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
 * 报修单（设计文档 4.3）。提交后只读，不提供撤回（BR01）。
 */
@Entity
@Table(name = "repair_request")
@Getter
@Setter
@NoArgsConstructor
public class RepairRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "request_no", nullable = false, length = 32, unique = true)
    private String requestNo;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "fault_type", length = 32)
    private String faultType;

    @Column(length = 16)
    private String urgency;

    @Column(name = "discovered_at", nullable = false)
    private LocalDateTime discoveredAt;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(nullable = false, length = 16)
    private RequestStatus status;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "submit_token", nullable = false, length = 64)
    private String submitToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RequestSource source = RequestSource.WEB;
}
