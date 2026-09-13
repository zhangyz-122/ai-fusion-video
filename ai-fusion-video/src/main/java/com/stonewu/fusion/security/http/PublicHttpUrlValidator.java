package com.stonewu.fusion.security.http;

import java.net.Inet4Address;
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
     * 抛业务异常的入口见 {@link SafeHttpDownloader#requirePublicUrl(String, String)}。
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
            return !isIpv6UniqueLocal(address)
                    && !isIpv4CompatibleIpv6(address)
                    && !isTunnelledPrivateIpv4(address);
        }
        // CGNAT 共享地址段（RFC 6598，100.64.0.0/10）：Java 不视为私有地址，
        // 但该段普遍用于运营商内部网络与 Tailscale 等主机间组网，不属于公网
        if (address instanceof Inet4Address) {
            byte[] bytes = address.getAddress();
            return !(bytes[0] == 100 && (bytes[1] & 0xC0) == 0x40);
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

    /**
     * 拦截嵌入内网 IPv4 的隧道地址：6to4（2002::/16，嵌入 IPv4 位于第 2-5 字节）
     * 与 NAT64（64:ff9b::/96，嵌入 IPv4 位于末 4 字节）。在具备对应路由的网络中，
     * 此类地址可落地到内网 IPv4，按嵌入的 IPv4 是否公网判定。
     */
    private static boolean isTunnelledPrivateIpv4(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 16) {
            return false;
        }
        if ((bytes[0] & 0xFF) == 0x20 && (bytes[1] & 0xFF) == 0x02) {
            return !isPublicIpv4Bytes(bytes, 2);
        }
        if ((bytes[0] & 0xFF) == 0x00 && (bytes[1] & 0xFF) == 0x64
                && (bytes[2] & 0xFF) == 0xFF && (bytes[3] & 0xFF) == 0x9B
                && isZeroRegion(bytes, 4, 12)) {
            return !isPublicIpv4Bytes(bytes, 12);
        }
        return false;
    }

    private static boolean isPublicIpv4Bytes(byte[] bytes, int offset) {
        try {
            return isPublicAddress(InetAddress.getByAddress(
                    new byte[]{bytes[offset], bytes[offset + 1], bytes[offset + 2], bytes[offset + 3]}));
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static boolean isZeroRegion(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
