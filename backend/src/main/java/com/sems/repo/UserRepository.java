package com.sems.repo;

import com.sems.domain.Role;
import com.sems.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByAccount(String account);

    boolean existsByAccount(String account);

    List<User> findByRoleAndEnabledTrueOrderByNameAsc(Role role);

    long countByRoleAndEnabledTrue(Role role);
}
