package com.sems.repo;

import com.sems.entity.Acceptance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AcceptanceRepository extends JpaRepository<Acceptance, Long> {

    List<Acceptance> findByOrderIdOrderByAcceptedAtAsc(Long orderId);

    List<Acceptance> findByOrderIdInOrderByOrderIdAscAcceptedAtAsc(List<Long> orderIds);
}
