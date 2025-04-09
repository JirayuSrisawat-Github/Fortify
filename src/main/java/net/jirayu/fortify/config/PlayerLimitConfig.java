package net.jirayu.fortify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.fortify.playerlimit")
@Component
public class PlayerLimitConfig {
    private boolean enabled = false;
    private int maxPlayers = 100;
    private boolean closeOnExceed = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public boolean isCloseOnExceed() {
        return closeOnExceed;
    }

    public void setCloseOnExceed(boolean closeOnExceed) {
        this.closeOnExceed = closeOnExceed;
    }
}