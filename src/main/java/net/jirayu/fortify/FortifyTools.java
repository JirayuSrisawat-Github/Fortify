package net.jirayu.fortify;

import jakarta.servlet.http.HttpServletRequest;
import net.jirayu.fortify.config.ProxyConfig;

public class FortifyTools {
    public static String getIp(HttpServletRequest request, ProxyConfig proxyConfig) {
        String ip = proxyConfig.isTrustProxy()
                ? request.getHeader(proxyConfig.getProxyHeader())
                : request.getRemoteAddr();

        return (ip == null || ip.isEmpty()) ? request.getRemoteAddr() : ip;
    }
}
