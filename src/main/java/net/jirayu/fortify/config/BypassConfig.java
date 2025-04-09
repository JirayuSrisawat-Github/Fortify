package net.jirayu.fortify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.fortify.bypass")
@Component
public class BypassConfig {
    private String[] allowedIps = new String[0];
    private Long[] allowedClients = new Long[0];

    public String[] getAllowedIps() {
        return allowedIps;
    }

    public void setAllowedIps(String[] allowedIps) {
        this.allowedIps = allowedIps;
    }

    public Long[] getAllowedClients() {
        return allowedClients;
    }

    public void setAllowedClients(Long[] allowedClients) {
        this.allowedClients = allowedClients;
    }
}
