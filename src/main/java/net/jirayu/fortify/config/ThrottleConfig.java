package net.jirayu.fortify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.fortify.throttle")
@Component
public class ThrottleConfig {
    private boolean enabled = false;
    private double cpuThreshold = 80.0;
    private double memoryThreshold = 80.0;
    private int connectionDelay = 1000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getCpuThreshold() {
        return cpuThreshold;
    }

    public void setCpuThreshold(double cpuThreshold) {
        this.cpuThreshold = cpuThreshold;
    }

    public double getMemoryThreshold() {
        return memoryThreshold;
    }

    public void setMemoryThreshold(double memoryThreshold) {
        this.memoryThreshold = memoryThreshold;
    }

    public int getConnectionDelay() {
        return connectionDelay;
    }

    public void setConnectionDelay(int connectionDelay) {
        this.connectionDelay = connectionDelay;
    }
} 