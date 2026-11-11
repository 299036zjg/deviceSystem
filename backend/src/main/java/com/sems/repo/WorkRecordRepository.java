package com.sems.repo;

import com.sems.entity.WorkRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkRecordRepository extends JpaRepository<WorkRecord, Long> {

    List<WorkRecord> findByOrderIdOrderBySubmittedAtAsc(Long orderId);

    List<WorkRecord> findByOrderIdInOrderByOrderIdAscSubmittedAtAsc(List<Long> orderIds);

    long countByOrderId(Long orderId);
}
