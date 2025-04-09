package net.jirayu.fortify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.fortify.proxy")
@Component
public class ProxyConfig {
    private boolean trustProxy = false;
    private String proxyHeader = "X-Forwarded-For";

    public void setTrustProxy(boolean trustProxy) {
        this.trustProxy = trustProxy;
    }

    public void setProxyHeader(String proxyHeader) {
        this.proxyHeader = proxyHeader;
    }

    public boolean isTrustProxy() {
        return trustProxy;
    }

    public String getProxyHeader() {
        return proxyHeader;
    }
}
