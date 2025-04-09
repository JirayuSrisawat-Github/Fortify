package net.jirayu.fortify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.fortify.ratelimit")
@Component
public class RateLimitConfig {
    private boolean enabled = false;
    private int maxRequests = 100;
    private int duration = 60;
    private int blockThreshold = 10;
    private int blockDuration = 300;
    private boolean blockWithIptables = false;

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public int getDuration() {
        return duration;
    }

    public int getBlockThreshold() {
        return blockThreshold;
    }

    public int getBlockDuration() {
        return blockDuration;
    }
    
    public boolean isBlockWithIptables() {
        return blockWithIptables;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public void setBlockThreshold(int blockThreshold) {
        this.blockThreshold = blockThreshold;
    }

    public void setBlockDuration(int blockDuration) {
        this.blockDuration = blockDuration;
    }
    
    public void setBlockWithIptables(boolean blockWithIptables) {
        this.blockWithIptables = blockWithIptables;
    }
}
