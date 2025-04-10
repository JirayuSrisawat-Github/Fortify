package net.jirayu.fortify.api;

import dev.arbjerg.lavalink.api.ISocketContext;
import net.jirayu.fortify.FortifySocket;
import net.jirayu.fortify.config.ApiConfig;
import net.jirayu.fortify.config.BypassConfig;
import net.jirayu.fortify.config.PathBlockConfig;
import net.jirayu.fortify.config.RateLimitConfig;
import net.jirayu.fortify.ratelimit.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/fortify")
public class FortifyApiController {
    private static final Logger log = LoggerFactory.getLogger(FortifyApiController.class);
    
    private final RateLimiter rateLimiter;
    private final FortifySocket fortifySocket;
    private final RateLimitConfig rateLimitConfig;
    private final BypassConfig bypassConfig;
    private final ApiConfig apiConfig;
    private final PathBlockConfig pathBlockConfig;

    public FortifyApiController(RateLimiter rateLimiter, 
                               FortifySocket fortifySocket,
                               RateLimitConfig rateLimitConfig,
                               BypassConfig bypassConfig,
                               ApiConfig apiConfig,
                               PathBlockConfig pathBlockConfig) {
        this.rateLimiter = rateLimiter;
        this.fortifySocket = fortifySocket;
        this.rateLimitConfig = rateLimitConfig;
        this.bypassConfig = bypassConfig;
        this.apiConfig = apiConfig;
        this.pathBlockConfig = pathBlockConfig;
    }
    
    private ResponseEntity<?> checkApiKey(String apiKey) {
        if (!apiConfig.isEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("API access is disabled");
        }
        
        if (apiKey == null || !apiKey.equals(apiConfig.getApiKey())) {
            log.warn("Unauthorized API access attempt with invalid key: {}", apiKey);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Invalid API key");
        }
        
        return null;
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@RequestHeader(value = "X-Fortify-Key", required = false) String apiKey) {
        ResponseEntity<?> authCheck = checkApiKey(apiKey);
        if (authCheck != null) return authCheck;
        
        Map<String, Object> status = new HashMap<>();
        
        status.put("activeConnections", fortifySocket.getConnectedSockets().size());

        Map<String, Object> connectionsInfo = new HashMap<>();
        connectionsInfo.put("total", fortifySocket.getConnectedSockets().size());
        fortifySocket.getConnectedSockets().forEach((socketId, socketContext) -> {
            connectionsInfo.put("userId", socketContext.getUserId());
            connectionsInfo.put("sessionId", socketContext.getSessionId());
            connectionsInfo.put("players", socketContext.getPlayers().size());
            connectionsInfo.put("isAllowed", fortifySocket.isAllowedId(socketContext.getUserId()));
        });
        status.put("connections", connectionsInfo);
        
        Map<String, Object> rateLimitInfo = new HashMap<>();
        rateLimitInfo.put("enabled", rateLimitConfig.isEnabled());
        rateLimitInfo.put("maxRequests", rateLimitConfig.getMaxRequests());
        rateLimitInfo.put("duration", rateLimitConfig.getDuration());
        status.put("rateLimit", rateLimitInfo);
        
        Map<String, Object> bypassInfo = new HashMap<>();
        bypassInfo.put("allowedIpCount", bypassConfig.getAllowedIps().length);
        bypassInfo.put("allowedClientCount", bypassConfig.getAllowedClients().length);
        status.put("bypass", bypassInfo);

        Map<String, Object> blockedInfo = new HashMap<>();
        blockedInfo.put("blockedIpCount", rateLimiter.getBlockedIpsWithExpiryTime().size());
        blockedInfo.put("blockDuration", rateLimitConfig.getBlockDuration());
        status.put("blocked", blockedInfo);

        Map<String, Object> pathBlockInfo = new HashMap<>();
        pathBlockInfo.put("enabled", pathBlockConfig.isEnabled());
        pathBlockInfo.put("blockedPaths", pathBlockConfig.getBlockedPaths());
        pathBlockInfo.put("blockedPathPatterns", pathBlockConfig.getBlockedPathPatterns());
        status.put("pathBlock", pathBlockInfo);
        
        return ResponseEntity.ok(status);
    }

    @GetMapping("/pathblock/paths")
    public ResponseEntity<?> getBlockedPaths(@RequestHeader(value = "X-Fortify-Key", required = false) String apiKey) {
        ResponseEntity<?> authCheck = checkApiKey(apiKey);
        if (authCheck != null) return authCheck;

        Map<String, Object> result = new HashMap<>();
        result.put("enabled", pathBlockConfig.isEnabled());
        result.put("blockedPaths", pathBlockConfig.getBlockedPaths());
        result.put("blockedPathPatterns", pathBlockConfig.getBlockedPathPatterns());

        return ResponseEntity.ok(result);
    }

    @PostMapping("/pathblock/add/path")
    public ResponseEntity<?> addBlockedPath(
            @RequestParam String path,
            @RequestHeader(value = "X-Fortify-Key", required = false) String apiKey) {
        ResponseEntity<?> authCheck = checkApiKey(apiKey);
        if (authCheck != null) return authCheck;

        String[] currentPaths = pathBlockConfig.getBlockedPaths();
        if (Arrays.asList(currentPaths).contains(path)) {
            return ResponseEntity.ok("Path " + path + " is already blocked");
        }

        String[] newPaths = Arrays.copyOf(currentPaths, currentPaths.length + 1);
        newPaths[currentPaths.length] = path;
        pathBlockConfig.setBlockedPaths(newPaths);

        return ResponseEntity.ok("Path " + path + " has been added to blocked paths");
    }

    @PostMapping("/pathblock/add/pattern")
    public ResponseEntity<?> addBlockedPathPattern(
            @RequestParam String pattern,
            @RequestHeader(value = "X-Fortify-Key", required = false) String apiKey) {
        ResponseEntity<?> authCheck = checkApiKey(apiKey);
        if (authCheck != null) return authCheck;

        try {
            String[] currentPatterns = pathBlockConfig.getBlockedPathPatterns();
            if (Arrays.asList(currentPatterns).contains(pattern)) {
                return ResponseEntity.ok("Pattern " + pattern + " is already blocked");
            }

            String[] newPatterns = Arrays.copyOf(currentPatterns, currentPatterns.length + 1);
            newPatterns[currentPatterns.length] = pattern;
            pathBlockConfig.setBlockedPathPatterns(newPatterns);

            return ResponseEntity.ok("Pattern " + pattern + " has been added to blocked patterns");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid regex pattern: " + e.getMessage());
        }
    }

    @PostMapping("/pathblock/remove/path")
    public ResponseEntity<?> removeBlockedPath(
            @RequestParam String path,
            @RequestHeader(value = "X-Fortify-Key", required = false) String apiKey) {
        ResponseEntity<?> authCheck = checkApiKey(apiKey);
        if (authCheck != null) return authCheck;

        String[] currentPaths = pathBlockConfig.getBlockedPaths();
        if (!Arrays.asList(currentPaths).contains(path)) {
            return ResponseEntity.ok("Path " + path + " is not in the blocked paths list");
        }

        String[] newPaths = Arrays.stream(currentPaths)
                .filter(p -> !p.equals(path))
                .toArray(String[]::new);
        pathBlockConfig.setBlockedPaths(newPaths);

        return ResponseEntity.ok("Path " + path + " has been removed from blocked paths");
    }

    @PostMapping("/pathblock/remove/pattern")
    public ResponseEntity<?> removeBlockedPathPattern(
            @RequestParam String pattern,
            @RequestHeader(value = "X-Fortify-Key", required = false) String apiKey) {
        ResponseEntity<?> authCheck = checkApiKey(apiKey);
        if (authCheck != null) return authCheck;

        String[] currentPatterns = pathBlockConfig.getBlockedPathPatterns();
        if (!Arrays.asList(currentPatterns).contains(pattern)) {
            return ResponseEntity.ok("Pattern " + pattern + " is not in the blocked patterns list");
        }

        String[] newPatterns = Arrays.stream(currentPatterns)
                .filter(p -> !p.equals(pattern))
                .toArray(String[]::new);
        pathBlockConfig.setBlockedPathPatterns(newPatterns);

        return ResponseEntity.ok("Pattern " + pattern + " has been removed from blocked patterns");
    }
    
    @PostMapping("/block/{ip}")
    public ResponseEntity<?> blockIp(
            @PathVariable String ip,
            @RequestHeader(value = "X-Fortify-Key", required = false) String apiKey) {
        ResponseEntity<?> authCheck = checkApiKey(apiKey);
        if (authCheck != null) return authCheck;
        
        rateLimiter.manuallyBlockIp(ip, rateLimitConfig.getBlockDuration() * 1000L);
        return ResponseEntity.ok("IP " + ip + " has been blocked");
    }
    
    @PostMapping("/unblock/{ip}")
    public ResponseEntity<?> unblockIp(
            @PathVariable String ip,
            @RequestHeader(value = "X-Fortify-Key", required = false) String apiKey) {
        ResponseEntity<?> authCheck = checkApiKey(apiKey);
        if (authCheck != null) return authCheck;
        
        rateLimiter.manuallyUnblockIp(ip);
        return ResponseEntity.ok("IP " + ip + " has been unblocked");
    }
    
    @GetMapping("/blocked")
    public ResponseEntity<?> getBlockedIps(
            @RequestHeader(value = "X-Fortify-Key", required = false) String apiKey) {
        ResponseEntity<?> authCheck = checkApiKey(apiKey);
        if (authCheck != null) return authCheck;
        
        return ResponseEntity.ok(rateLimiter.getBlockedIpsWithExpiryTime());
    }
} 