package com.inventory.config;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after 5 wrong passwords for the same username from the same
 * address, sign-in is blocked for 10 minutes. State is kept in memory (cleared on restart).
 */
final class LoginThrottle {
    static final int MAX_FAILURES = 5;
    static final Duration LOCK = Duration.ofMinutes(10);

    private record State(int failures, Instant lockedUntil, Instant last) {}
    private final ConcurrentHashMap<String, State> attempts = new ConcurrentHashMap<>();

    static String key(HttpServletRequest request) {
        String user = request.getParameter("username");
        return request.getRemoteAddr() + "|" + (user == null ? "" : user.trim().toLowerCase());
    }

    boolean isLocked(String key) {
        State s = attempts.get(key);
        return s != null && s.lockedUntil() != null && Instant.now().isBefore(s.lockedUntil());
    }

    void failed(String key) {
        Instant now = Instant.now();
        attempts.compute(key, (k, s) -> {
            int failures = (s == null || s.last().isBefore(now.minus(LOCK))) ? 1 : s.failures() + 1;
            return new State(failures, failures >= MAX_FAILURES ? now.plus(LOCK) : null, now);
        });
        if (attempts.size() > 10_000) {
            attempts.entrySet().removeIf(e -> e.getValue().last().isBefore(now.minus(Duration.ofHours(1))));
        }
    }

    void succeeded(String key) { attempts.remove(key); }
}
