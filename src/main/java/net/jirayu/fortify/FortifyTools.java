package net.jirayu.fortify;

import jakarta.servlet.http.HttpServletRequest;
import net.jirayu.fortify.config.ProxyConfig;

public class FortifyTools {
    public static String getIp(HttpServletRequest request, ProxyConfig proxyConfig) {
        String ip;

        if (proxyConfig.isTrustProxy()) {
            ip = request.getHeader(proxyConfig.getProxyHeader());
        } else {
            ip = request.getRemoteAddr();
        }

        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }

        return ip;
    }
}
