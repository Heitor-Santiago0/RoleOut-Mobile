package br.com.roleout.security;

import br.com.roleout.api.ApiException;
import java.util.HashMap;
import java.util.Map;

/** Bounded per-instance limiter. A shared gateway limiter is needed with replicas. */
public final class AuthRateLimiter {
    private record Window(long start, int count) { }
    private final Map<String, Window> windows = new HashMap<>();
    public synchronized void check(String address) {
        long now = System.currentTimeMillis();
        if (windows.size() >= 10000) windows.entrySet().removeIf(e -> now - e.getValue().start() >= 60000);
        Window current = windows.get(address);
        if (current == null && windows.size() >= 10000) throw limited();
        if (current == null || now - current.start() >= 60000) current = new Window(now, 0);
        if (current.count() >= 10) throw limited();
        windows.put(address, new Window(current.start(), current.count() + 1));
    }
    private ApiException limited() { return new ApiException(429, "LIMITE_TENTATIVAS", "Aguarde antes de tentar novamente."); }
}
