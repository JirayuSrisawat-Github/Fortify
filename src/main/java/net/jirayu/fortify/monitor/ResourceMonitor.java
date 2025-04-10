package net.jirayu.fortify.monitor;

import net.jirayu.fortify.config.ThrottleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ResourceMonitor {
    private static final Logger log = LoggerFactory.getLogger(ResourceMonitor.class);
    
    private final ThrottleConfig throttleConfig;
    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    private final AtomicBoolean throttling = new AtomicBoolean(false);
    
    public ResourceMonitor(ThrottleConfig throttleConfig) {
        this.throttleConfig = throttleConfig;
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();

        if (throttleConfig.isEnabled()) {
            startMonitoring();
        }
    }
    
    private void startMonitoring() {
        Thread monitorThread = new Thread(() -> {
            while (true) {
                try {
                    checkResources();
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
        log.info("Resource monitoring started");
    }
    
    private void checkResources() {
        double cpuLoad = osBean.getSystemLoadAverage();
        double memoryUsage = getMemoryUsagePercent();
        
        boolean shouldThrottle = cpuLoad > throttleConfig.getCpuThreshold() || 
                                 memoryUsage > throttleConfig.getMemoryThreshold();
        
        if (shouldThrottle && !throttling.getAndSet(true)) {
            log.warn("System resources high (CPU: {}%, Memory: {}%). Enabling connection throttling.", 
                     cpuLoad, memoryUsage);
        } else if (!shouldThrottle && throttling.getAndSet(false)) {
            log.info("System resources normalized (CPU: {}%, Memory: {}%). Disabling connection throttling.", 
                    cpuLoad, memoryUsage);
        }
    }
    
    private double getMemoryUsagePercent() {
        long used = memoryBean.getHeapMemoryUsage().getUsed();
        long max = memoryBean.getHeapMemoryUsage().getMax();
        return ((double) used / max) * 100.0;
    }
    
    public boolean isThrottling() {
        return throttling.get() && throttleConfig.isEnabled();
    }
    
    public int getConnectionDelay() {
        return throttleConfig.getConnectionDelay();
    }
} 