package com.sems.service;

import com.sems.common.BizException;
import com.sems.common.ErrorCode;
import com.sems.common.PageResult;
import com.sems.common.Paging;
import com.sems.domain.OrderStatus;
import com.sems.domain.Role;
import com.sems.entity.User;
import com.sems.repo.UserRepository;
import com.sems.repo.WorkOrderRepository;
import com.sems.security.AuthContext;
import com.sems.security.PasswordHasher;
import com.sems.security.SessionStore;
import com.sems.web.dto.UserDtos;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 账号管理（EM001）：新增、停用、启用、重置密码。
 * 账号只能停用不能删除（BR06）；创建后不直接修改角色（EM001 2.c）。
 */
@Service
public class UserService {

    private static final List<OrderStatus> UNFINISHED = List.of(
            OrderStatus.PENDING, OrderStatus.PROCESSING, OrderStatus.PENDING_ACCEPTANCE);

    private final UserRepository userRepository;
    private final WorkOrderRepository workOrderRepository;
    private final PasswordHasher passwordHasher;
    private final SessionStore sessionStore;

    public UserService(UserRepository userRepository, WorkOrderRepository workOrderRepository,
                       PasswordHasher passwordHasher, SessionStore sessionStore) {
        this.userRepository = userRepository;
        this.workOrderRepository = workOrderRepository;
        this.passwordHasher = passwordHasher;
        this.sessionStore = sessionStore;
    }

    @Transactional(readOnly = true)
    public PageResult<UserDtos.UserView> list(String keyword, String role, String department, Boolean enabled,
                                              Integer page, Integer size) {
        AuthContext.requireAdmin();
        Role roleFilter = role == null || role.isBlank() ? null : Role.from(role);
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(cb.like(root.get("account"), like), cb.like(root.get("name"), like)));
            }
            if (roleFilter != null) {
                predicates.add(cb.equal(root.get("role"), roleFilter));
            }
            if (department != null && !department.isBlank()) {
                predicates.add(cb.equal(root.get("department"), department.trim()));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return PageResult.of(userRepository.findAll(spec,
                        Paging.of(page, size, Sort.by(Sort.Direction.ASC, "userId")))
                .map(this::toView));
    }

    @Transactional(readOnly = true)
    public List<UserDtos.UserView> technicians() {
        AuthContext.requireAdmin();
        return userRepository.findByRoleAndEnabledTrueOrderByNameAsc(Role.TECHNICIAN)
                .stream().map(this::toView).toList();
    }

    @Transactional
    public UserDtos.UserView create(UserDtos.CreateRequest request) {
        AuthContext.requireAdmin();
        String account = request.account().trim();
        if (userRepository.existsByAccount(account)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账号 " + account + " 已存在，请更换账号");
        }
        Role role;
        try {
            role = Role.from(request.role());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, ex.getMessage());
        }
        if (!PasswordHasher.isValidPassword(request.password())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "初始" + PasswordHasher.describePasswordRule());
        }
        User user = new User();
        user.setAccount(account);
        user.setName(request.name().trim());
        user.setDepartment(request.department().trim());
        user.setRole(role);
        user.setPasswordHash(passwordHasher.hash(request.password()));
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        return toView(userRepository.save(user));
    }

    @Transactional
    public UserDtos.UserView setEnabled(Long userId, boolean enabled) {
        AuthContext.requireAdmin();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BizException.notFound("用户不存在"));
        if (user.isEnabled() == enabled) {
            return toView(user);
        }
        if (!enabled) {
            if (user.getRole() == Role.TECHNICIAN
                    && workOrderRepository.existsByOwnerIdAndStatusIn(userId, UNFINISHED)) {
                throw new BizException(ErrorCode.STATE_CONFLICT,
                        "该维修人员存在未完成工单，不能停用；请先完成或验收其工单");
            }
            if (user.getRole() == Role.ADMIN && userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "系统必须保留至少一个启用的管理员");
            }
        }
        user.setEnabled(enabled);
        userRepository.save(user);
        // 停用后既有会话失效（EM001 2.d）
        sessionStore.invalidateUser(userId);
        return toView(user);
    }

    @Transactional
    public void resetPassword(Long userId, String password) {
        AuthContext.requireAdmin();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BizException.notFound("用户不存在"));
        if (!PasswordHasher.isValidPassword(password)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "重置后的" + PasswordHasher.describePasswordRule());
        }
        user.setPasswordHash(passwordHasher.hash(password));
        // 重置后要求用户重新登录并修改本人密码（EM001 2.e）
        user.setMustChangePassword(true);
        userRepository.save(user);
        // 重置后该用户既有会话失效（EM001 2.e）
        sessionStore.invalidateUser(userId);
    }

    private UserDtos.UserView toView(User user) {
        return new UserDtos.UserView(user.getUserId(), user.getAccount(), user.getName(), user.getDepartment(),
                user.getRole().name(), user.getRole().label(), user.isEnabled(), user.getCreatedAt());
    }
}
