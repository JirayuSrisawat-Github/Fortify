package net.jirayu.fortify.ratelimit;

import net.jirayu.fortify.config.RateLimitConfig;
import net.jirayu.fortify.firewall.FirewallManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;

@Service
public class RateLimiter {
    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final RateLimitConfig config;
    private final FirewallManager firewallManager;
    private final Map<String, RequestTracker> requestTrackers = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedIps = new ConcurrentHashMap<>();

    public RateLimiter(RateLimitConfig config, FirewallManager firewallManager) {
        this.config = config;
        this.firewallManager = firewallManager;

        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    cleanup();
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.start();

        log.info("Rate limiter initialized with max {} requests per {} seconds",
                config.getMaxRequests(), config.getDuration());
    }

    public boolean isAllowed(String ip) {
        if (isBlocked(ip)) {
            log.debug("IP {} is currently blocked", ip);
            return false;
        }

        RequestTracker tracker = requestTrackers.computeIfAbsent(ip, k -> new RequestTracker());
        tracker.addRequest(config.getDuration() * 1000L);

        if (tracker.getRequestsInWindow() > config.getMaxRequests()) {
            tracker.incrementViolations();

            if (tracker.getViolations() >= config.getBlockThreshold()) {
                long blockDurationMillis = config.getBlockDuration() * 1000L;
                long unblockTime = System.currentTimeMillis() + blockDurationMillis;
                blockedIps.put(ip, unblockTime);

                log.warn("IP {} blocked for {} seconds due to rate limit violations",
                        ip, config.getBlockDuration());

                if (config.isBlockWithFirewall()) {
                    firewallManager.blockIp(ip);

                    Thread unblockThread = new Thread(() -> {
                        try {
                            Thread.sleep(blockDurationMillis);
                            firewallManager.unblockIp(ip);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    unblockThread.setDaemon(true);
                    unblockThread.start();
                }

                return false;
            }

            log.debug("Rate limit exceeded for IP {}: {} violations", ip, tracker.getViolations());
            return false;
        }

        return true;
    }

    public boolean isBlocked(String ip) {
        Long unblockTime = blockedIps.get(ip);
        if (unblockTime == null) {
            return false;
        }

        if (System.currentTimeMillis() > unblockTime) {
            blockedIps.remove(ip);
            if (config.isBlockWithFirewall()) {
                firewallManager.unblockIp(ip);
            }
            return false;
        }

        return true;
    }

    public int getRemainingRequests(String ip) {
        RequestTracker tracker = requestTrackers.get(ip);
        if (tracker == null) {
            return config.getMaxRequests();
        }

        int remaining = config.getMaxRequests() - tracker.getRequestsInWindow();
        return Math.max(0, remaining);
    }

    public long getWindowStart(String ip) {
        RequestTracker tracker = requestTrackers.get(ip);
        return tracker != null ? tracker.getWindowStart() : System.currentTimeMillis();
    }

    private void cleanup() {
        long now = System.currentTimeMillis();

        requestTrackers.entrySet().removeIf(entry -> {
            RequestTracker tracker = entry.getValue();
            if (tracker.getLastRequestTime() < now - (config.getDuration() * 2000L) &&
                    tracker.getViolations() == 0) {
                return true;
            }
            tracker.cleanup(config.getDuration() * 1000L);
            return false;
        });

        blockedIps.entrySet().removeIf(entry -> {
            boolean shouldRemove = now > entry.getValue();
            if (shouldRemove && config.isBlockWithFirewall()) {
                firewallManager.unblockIp(entry.getKey());
            }
            return shouldRemove;
        });
    }

    public void manuallyBlockIp(String ip, long durationMillis) {
        long unblockTime = System.currentTimeMillis() + durationMillis;
        blockedIps.put(ip, unblockTime);
        log.warn("IP {} manually blocked for {} milliseconds", ip, durationMillis);

        if (config.isBlockWithFirewall()) {
            firewallManager.blockIp(ip);

            Thread unblockThread = new Thread(() -> {
                try {
                    Thread.sleep(durationMillis);
                    firewallManager.unblockIp(ip);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            unblockThread.setDaemon(true);
            unblockThread.start();
        }
    }

    public void manuallyUnblockIp(String ip) {
        blockedIps.remove(ip);
        log.info("IP {} manually unblocked", ip);

        if (config.isBlockWithFirewall()) {
            firewallManager.unblockIp(ip);
        }
    }

    public Map<String, Object> getBlockedIpsWithExpiryTime() {
        return new HashMap<>(blockedIps);
    }

    private static class RequestTracker {
        private final Map<Long, Integer> requestsPerSecond = new ConcurrentHashMap<>();
        private int violations = 0;
        private long lastRequestTime = 0;
        private long windowStart;

        public void addRequest(long windowDurationMillis) {
            long now = System.currentTimeMillis();

            if (now - windowStart > windowDurationMillis) {
                requestsPerSecond.clear();
                violations = 0;
                windowStart = now;
            }

            long second = now / 1000;
            requestsPerSecond.compute(second, (k, v) -> (v == null) ? 1 : v + 1);
            lastRequestTime = now;
        }

        public long getWindowStart() {
            return windowStart;
        }

        public int getRequestsInWindow() {
            return requestsPerSecond.values().stream().mapToInt(Integer::intValue).sum();
        }

        public void incrementViolations() {
            violations++;
        }

        public int getViolations() {
            return violations;
        }

        public long getLastRequestTime() {
            return lastRequestTime;
        }

        public void cleanup(long windowDurationMillis) {
            long cutoffSecond = (System.currentTimeMillis() - windowDurationMillis) / 1000;
            requestsPerSecond.keySet().removeIf(second -> second < cutoffSecond);
        }
    }
}