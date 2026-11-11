package com.sems.service;

import com.sems.common.BizException;
import com.sems.common.ErrorCode;
import com.sems.entity.User;
import com.sems.repo.UserRepository;
import com.sems.security.AuthContext;
import com.sems.security.PasswordHasher;
import com.sems.security.Session;
import com.sems.security.SessionStore;
import com.sems.web.dto.AuthDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final SessionStore sessionStore;

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher, SessionStore sessionStore) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.sessionStore = sessionStore;
    }

    @Transactional(readOnly = true)
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        String account = request.account() == null ? "" : request.account().trim();
        // 账号不存在、密码错误、账号停用统一提示，不泄露账号状态（EM001 1.a、EM009 1.a）
        User user = userRepository.findByAccount(account)
                .filter(User::isEnabled)
                .filter(u -> passwordHasher.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "账号或密码错误，或账号已停用"));
        Session session = sessionStore.create(user);
        return new AuthDtos.LoginResponse(session.token(), user.getUserId(), user.getAccount(), user.getName(),
                user.getDepartment(), user.getRole().name(), user.getRole().label(), session.expireAt(),
                user.isMustChangePassword());
    }

    public void logout(String token) {
        sessionStore.invalidate(token);
    }

    @Transactional(readOnly = true)
    public AuthDtos.ProfileView profile() {
        User user = userRepository.findById(AuthContext.userId())
                .orElseThrow(() -> BizException.notFound("用户不存在"));
        return toProfile(user);
    }

    @Transactional
    public void changePassword(AuthDtos.ChangePasswordRequest request) {
        Long userId = AuthContext.userId();
        User user = userRepository.findById(userId).orElseThrow(() -> BizException.notFound("用户不存在"));
        if (!passwordHasher.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "原密码错误");
        }
        if (!PasswordHasher.isValidPassword(request.newPassword())) {
            throw new BizException(ErrorCode.PARAM_INVALID, PasswordHasher.describePasswordRule());
        }
        if (passwordHasher.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "新密码不能与原密码相同");
        }
        user.setPasswordHash(passwordHasher.hash(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        // 改密成功后旧会话全部失效（EM001 3.b）
        sessionStore.invalidateUser(userId);
    }

    private AuthDtos.ProfileView toProfile(User user) {
        return new AuthDtos.ProfileView(user.getUserId(), user.getAccount(), user.getName(),
                user.getDepartment(), user.getRole().name(), user.getRole().label(), user.isEnabled(),
                user.isMustChangePassword());
    }
}
