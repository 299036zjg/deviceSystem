package com.sems.repo;

import com.sems.entity.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long>, JpaSpecificationExecutor<Equipment> {

    Optional<Equipment> findByEquipmentCode(String equipmentCode);

    boolean existsByEquipmentCode(String equipmentCode);

    List<Equipment> findByEquipmentIdIn(List<Long> equipmentIds);

    List<Equipment> findByEnabledTrueOrderByEquipmentCodeAsc();

    long countByEnabledTrue();
}
