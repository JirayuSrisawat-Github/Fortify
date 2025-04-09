package net.jirayu.fortify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.fortify.notification")
@Component
public class NotificationConfig {
    private String discordWebhookUrl = "";
    private Enabled enabled = new Enabled();

    public static class Enabled {
        private boolean ratelimit = false;
        private boolean playerLimit = false;

        public boolean isRatelimit() {
            return ratelimit;
        }

        public void setRatelimit(boolean ratelimit) {
            this.ratelimit = ratelimit;
        }

        public boolean isPlayerLimit() {
            return playerLimit;
        }

        public void setPlayerLimit(boolean playerLimit) {
            this.playerLimit = playerLimit;
        }
    }

    public String getDiscordWebhookUrl() {
        return discordWebhookUrl;
    }

    public void setDiscordWebhookUrl(String discordWebhookUrl) {
        this.discordWebhookUrl = discordWebhookUrl;
    }

    public Enabled getEnabled() {
        return enabled;
    }

    public void setEnabled(Enabled enabled) {
        this.enabled = enabled;
    }
}