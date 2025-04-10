package net.jirayu.fortify.ratelimit;

import net.jirayu.fortify.config.RateLimitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;

@Service
public class RateLimiter {
    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final RateLimitConfig config;
    private final Map<String, RequestTracker> requestTrackers = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedIps = new ConcurrentHashMap<>();

    public RateLimiter(RateLimitConfig config) {
        this.config = config;

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
        tracker.addRequest();

        if (tracker.getRequestsInWindow() > config.getMaxRequests()) {
            tracker.incrementViolations();

            if (tracker.getViolations() >= config.getBlockThreshold()) {
                long blockDurationMillis = config.getBlockDuration() * 1000L;
                long unblockTime = System.currentTimeMillis() + blockDurationMillis;
                blockedIps.put(ip, unblockTime);

                log.warn("IP {} blocked for {} seconds due to rate limit violations",
                        ip, config.getBlockDuration());
                
                if (config.isBlockWithFirewall()) {
                    blockIpWithFirewall(ip);

                    Thread unblockThread = new Thread(() -> {
                        try {
                            Thread.sleep(blockDurationMillis);
                            unblockIpWithFirewall(ip);
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
                unblockIpWithFirewall(ip);
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
                unblockIpWithFirewall(entry.getKey());
            }
            return shouldRemove;
        });
    }
    
    private void blockIpWithFirewall(String ip) {
        if ("ufw".equalsIgnoreCase(config.getFirewallType())) {
            blockIpWithUfw(ip);
        } else {
            blockIpWithIptables(ip);
        }
    }
    
    private void unblockIpWithFirewall(String ip) {
        if ("ufw".equalsIgnoreCase(config.getFirewallType())) {
            unblockIpWithUfw(ip);
        } else {
            unblockIpWithIptables(ip);
        }
    }
    
    private void blockIpWithIptables(String ip) {
        try {
            String inputCommand = String.format("iptables -A INPUT -s %s -j DROP", ip);
            Process inputProcess = Runtime.getRuntime().exec(inputCommand);
            int inputExitCode = inputProcess.waitFor();
            
            if (inputExitCode == 0) {
                log.info("Successfully blocked IP {} in INPUT chain with iptables", ip);
            } else {
                log.error("Failed to block IP {} in INPUT chain with iptables, exit code: {}", ip, inputExitCode);
                logProcessError(inputProcess);
            }

            String forwardCommand = String.format("iptables -A FORWARD -s %s -j DROP", ip);
            Process forwardProcess = Runtime.getRuntime().exec(forwardCommand);
            int forwardExitCode = forwardProcess.waitFor();
            
            if (forwardExitCode == 0) {
                log.info("Successfully blocked IP {} in FORWARD chain with iptables", ip);
            } else {
                log.error("Failed to block IP {} in FORWARD chain with iptables, exit code: {}", ip, forwardExitCode);
                logProcessError(forwardProcess);
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error executing iptables block command for IP {}: {}", ip, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private void unblockIpWithIptables(String ip) {
        try {
            String inputCommand = String.format("iptables -D INPUT -s %s -j DROP", ip);
            Process inputProcess = Runtime.getRuntime().exec(inputCommand);
            int inputExitCode = inputProcess.waitFor();
            
            if (inputExitCode == 0) {
                log.info("Successfully unblocked IP {} from INPUT chain with iptables", ip);
            } else {
                log.error("Failed to unblock IP {} from INPUT chain with iptables, exit code: {}", ip, inputExitCode);
                logProcessError(inputProcess);
            }

            String forwardCommand = String.format("iptables -D FORWARD -s %s -j DROP", ip);
            Process forwardProcess = Runtime.getRuntime().exec(forwardCommand);
            int forwardExitCode = forwardProcess.waitFor();
            
            if (forwardExitCode == 0) {
                log.info("Successfully unblocked IP {} from FORWARD chain with iptables", ip);
            } else {
                log.error("Failed to unblock IP {} from FORWARD chain with iptables, exit code: {}", ip, forwardExitCode);
                logProcessError(forwardProcess);
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error executing iptables unblock command for IP {}: {}", ip, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private void blockIpWithUfw(String ip) {
        try {
            String command = String.format("ufw deny from %s to any", ip);
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                log.info("Successfully blocked IP {} with UFW", ip);
            } else {
                log.error("Failed to block IP {} with UFW, exit code: {}", ip, exitCode);
                logProcessError(process);
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error executing UFW block command for IP {}: {}", ip, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private void unblockIpWithUfw(String ip) {
        try {
            String command = String.format("ufw delete deny from %s to any", ip);
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                log.info("Successfully unblocked IP {} with UFW", ip);
            } else {
                log.error("Failed to unblock IP {} with UFW, exit code: {}", ip, exitCode);
                logProcessError(process);
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error executing UFW unblock command for IP {}: {}", ip, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private void logProcessError(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            StringBuilder errorOutput = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
            if (errorOutput.length() > 0) {
                log.error("Process error output: {}", errorOutput.toString());
            }
        } catch (IOException e) {
            log.error("Error reading process error stream: {}", e.getMessage());
        }
    }

    public void manuallyBlockIp(String ip, long durationMillis) {
        long unblockTime = System.currentTimeMillis() + durationMillis;
        blockedIps.put(ip, unblockTime);
        log.warn("IP {} manually blocked for {} milliseconds", ip, durationMillis);
        
        if (config.isBlockWithFirewall()) {
            blockIpWithFirewall(ip);
            
            Thread unblockThread = new Thread(() -> {
                try {
                    Thread.sleep(durationMillis);
                    unblockIpWithFirewall(ip);
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
            unblockIpWithFirewall(ip);
        }
    }

    public Map<String, Object> getBlockedIpsWithExpiryTime() {
        return new HashMap<>(blockedIps);
    }

    private static class RequestTracker {
        private final Map<Long, Integer> requestsPerSecond = new ConcurrentHashMap<>();
        private int violations = 0;
        private long lastRequestTime = 0;

        public void addRequest() {
            long now = System.currentTimeMillis();
            long second = now / 1000;

            requestsPerSecond.compute(second, (k, v) -> (v == null) ? 1 : v + 1);
            lastRequestTime = now;
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