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

import java.time.LocalDateTime;

/**
 * 设备（设计文档 4.2）。二维码内容由 equipment_code 派生，设备编号创建后不可修改（BR07）。
 */
@Entity
@Table(name = "equipment")
@Getter
@Setter
@NoArgsConstructor
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "equipment_code", nullable = false, length = 32, unique = true)
    private String equipmentCode;

    @Column(name = "equipment_name", nullable = false, length = 100)
    private String equipmentName;

    @Column(length = 50)
    private String category;

    @Column(length = 100)
    private String model;

    @Column(nullable = false, length = 50)
    private String department;

    @Column(nullable = false, length = 100)
    private String location;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
