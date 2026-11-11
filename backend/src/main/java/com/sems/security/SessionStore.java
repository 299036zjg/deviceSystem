package com.sems.security;

import com.sems.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存令牌会话（设计文档 5.2、3.5.4）。
 * 同一账号可在多台设备登录；退出、改密、重置或停用后该用户全部令牌失效。
 */
@Component
public class SessionStore {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> tokensByUser = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Value("${sems.session.ttl-minutes:120}")
    private long ttlMinutes = 120;

    public Session create(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Session session = new Session(token, user.getUserId(), user.getName(), user.getRole(),
                user.getDepartment(), LocalDateTime.now().plusMinutes(ttlMinutes));
        sessions.put(token, session);
        tokensByUser.computeIfAbsent(user.getUserId(), k -> ConcurrentHashMap.newKeySet()).add(token);
        return session;
    }

    public Optional<Session> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expired()) {
            sessions.remove(token);
            removeFromUser(session);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void invalidate(String token) {
        Session session = sessions.remove(token);
        if (session != null) {
            removeFromUser(session);
        }
    }

    /**
     * 使该用户的全部既有会话失效（EM001 3.b、2.d、2.e）。
     */
    public void invalidateUser(Long userId) {
        Set<String> tokens = tokensByUser.remove(userId);
        if (tokens != null) {
            tokens.forEach(sessions::remove);
        }
    }

    private void removeFromUser(Session session) {
        Set<String> tokens = tokensByUser.get(session.userId());
        if (tokens != null) {
            tokens.remove(session.token());
            if (tokens.isEmpty()) {
                tokensByUser.remove(session.userId());
            }
        }
    }
}
