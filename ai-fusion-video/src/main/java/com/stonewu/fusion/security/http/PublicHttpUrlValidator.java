package com.stonewu.fusion.security.http;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * SSRF 防护：判断 http(s) URL 是否指向公网地址。
 * <p>
 * 校验顺序为“先解析、后校验”：对域名执行 DNS 解析，并对全部解析结果逐一校验，
 * 防止攻击者用解析到内网的域名绕过字面量检查（基础版 DNS rebinding 防护；
 * 连接时二次解析导致的 TOCTOU 需要连接级 IP 固定才能彻底解决）。
 */
public final class PublicHttpUrlValidator {

    private static final DnsResolver SYSTEM_RESOLVER = InetAddress::getAllByName;

    private PublicHttpUrlValidator() {
    }

    /**
     * DNS 解析函数抽象，便于单元测试注入固定映射。
     */
    @FunctionalInterface
    public interface DnsResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    /**
     * 仅当 URL 为 http(s) 协议且主机名（含全部 DNS 解析结果）均为公网地址时返回 true。
     */
    public static boolean isAllowedPublicHttpUrl(String value) {
        return isAllowedPublicHttpUrl(value, SYSTEM_RESOLVER);
    }

    public static boolean isAllowedPublicHttpUrl(String value, DnsResolver resolver) {
        String normalized = value == null ? "" : value.trim();
        if (!isHttpScheme(normalized)) {
            return false;
        }
        String host;
        try {
            URI uri = new URI(normalized);
            host = uri.getHost();
        } catch (Exception e) {
            return false;
        }
        host = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (isForbiddenHostname(host)) {
            return false;
        }
        try {
            InetAddress[] addresses = resolver.resolve(host);
            if (addresses == null || addresses.length == 0) {
                return false;
            }
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static boolean isHttpScheme(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static boolean isForbiddenHostname(String host) {
        return host.isEmpty()
                || "localhost".equals(host)
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal")
                || "host.docker.internal".equals(host)
                || "gateway.docker.internal".equals(host);
    }

    private static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet6Address) {
            return !isIpv6UniqueLocal(address) && !isIpv4CompatibleIpv6(address);
        }
        return true;
    }

    private static boolean isIpv6UniqueLocal(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    /**
     * 拦截 IPv4 兼容形式的 IPv6（如 ::127.0.0.1）：其低 32 位可能指向内网。
     * IPv4 映射形式（::ffff:127.0.0.1）在 Java 中已归一化为 Inet4Address，由上方通用检查覆盖。
     */
    private static boolean isIpv4CompatibleIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 16) {
            return false;
        }
        for (int i = 0; i < 12; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        byte[] ipv4 = new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
        try {
            return !isPublicAddress(InetAddress.getByAddress(ipv4));
        } catch (UnknownHostException e) {
            return true;
        }
    }
}
