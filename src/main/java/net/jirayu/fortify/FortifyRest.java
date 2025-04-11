package net.jirayu.fortify;

import dev.arbjerg.lavalink.api.RestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.jirayu.fortify.config.BypassConfig;
import net.jirayu.fortify.config.NotificationConfig;
import net.jirayu.fortify.config.PathBlockConfig;
import net.jirayu.fortify.config.ProxyConfig;
import net.jirayu.fortify.config.RateLimitConfig;
import net.jirayu.fortify.notification.NotificationService;
import net.jirayu.fortify.ratelimit.RateLimiter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FortifyRest implements RestInterceptor {
    private static final Logger log = LoggerFactory.getLogger(FortifyRest.class);

    private final RateLimitConfig rateLimitConfig;
    private final ProxyConfig proxyConfig;
    private final BypassConfig bypassConfig;
    private final NotificationConfig notificationConfig;
    private final PathBlockConfig pathBlockConfig;
    private final RateLimiter rateLimiter;
    private final NotificationService notificationService;
    private final Map<String, Long> windowStartMap = new ConcurrentHashMap<>();

    public FortifyRest(RateLimitConfig rateLimitConfig,
                       ProxyConfig proxyConfig,
                       BypassConfig bypassConfig,
                       NotificationConfig notificationConfig,
                       PathBlockConfig pathBlockConfig,
                       RateLimiter rateLimiter,
                       NotificationService notificationService) {
        this.rateLimitConfig = rateLimitConfig;
        this.proxyConfig = proxyConfig;
        this.bypassConfig = bypassConfig;
        this.notificationConfig = notificationConfig;
        this.pathBlockConfig = pathBlockConfig;
        this.rateLimiter = rateLimiter;
        this.notificationService = notificationService;
    }

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request,
                             @NotNull HttpServletResponse response,
                             @NotNull Object handler) {
        String ip = FortifyTools.getIp(request, proxyConfig);
        String path = request.getRequestURI();
        log.debug("Processing request from IP {} to path {}", ip, path);

        if (pathBlockConfig.isEnabled() && pathBlockConfig.isPathBlocked(path) && !isAllowedIp(ip)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            log.warn("Blocked request to restricted path: {} from IP {}", path, ip);

            if (notificationConfig.getEnabled().isPathBlock()) {
                notificationService.sendPathBlockNotification(ip, path);
            }

            return false;
        }

        if (isAllowedIp(ip)) {
            log.debug("Request allowed for whitelisted IP {}", ip);
            return true;
        }

        boolean wasAlreadyBlocked = rateLimiter.isBlocked(ip);
        boolean allowed = rateLimiter.isAllowed(ip);
        long windowStart = rateLimiter.getWindowStart(ip);
        long resetTime = windowStart + (rateLimitConfig.getDuration() * 1000L);

        if (!allowed) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimitConfig.getMaxRequests()));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("X-RateLimit-Used", String.valueOf(rateLimitConfig.getMaxRequests()));
            response.setHeader("X-RateLimit-Reset", String.valueOf(resetTime));
            response.setHeader("Retry-After", String.valueOf(rateLimitConfig.getDuration()));

            if (rateLimiter.isBlocked(ip)) {
                log.warn("Blocked request from IP {} to {}", ip, path);

                if (!wasAlreadyBlocked && notificationConfig.getEnabled().isRatelimit()) {
                    notificationService.sendBlockNotification(ip, path);
                }
            } else {
                log.debug("Rate limited request from IP {} to {}", ip, path);
            }

            return false;
        }

        int remaining = rateLimiter.getRemainingRequests(ip);
        response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimitConfig.getMaxRequests()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Used", String.valueOf(rateLimitConfig.getMaxRequests() - remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetTime));

        log.debug("Request allowed for IP {}: {}/{} requests used", ip,
                rateLimitConfig.getMaxRequests() - remaining, rateLimitConfig.getMaxRequests());
        return true;
    }

    private boolean isAllowedIp(String ip) {
        return Arrays.asList(bypassConfig.getAllowedIps()).contains(ip);
    }
}