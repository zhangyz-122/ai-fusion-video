package com.stonewu.fusion.security.http;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PublicHttpUrlValidatorTests {

    private final Map<String, InetAddress[]> dnsTable = new HashMap<>();

    private void givenDnsHost(String host, String... ipLiterals) throws UnknownHostException {
        InetAddress[] addresses = new InetAddress[ipLiterals.length];
        for (int i = 0; i < ipLiterals.length; i++) {
            addresses[i] = InetAddress.getByName(ipLiterals[i]);
        }
        dnsTable.put(host, addresses);
    }

    private boolean validateWithFakeDns(String url) {
        return PublicHttpUrlValidator.isAllowedPublicHttpUrl(url, dnsTable::get);
    }

    @Test
    void acceptsHttpsUrlWhoseDnsResolvesToPublicAddress() throws UnknownHostException {
        givenDnsHost("cdn.example.com", "93.184.216.34");

        assertThat(validateWithFakeDns("https://cdn.example.com/image.png")).isTrue();
    }

    @Test
    void acceptsUrlWhenAllResolvedAddressesArePublic() throws UnknownHostException {
        givenDnsHost("cdn.example.com", "93.184.216.34", "151.101.1.140");

        assertThat(validateWithFakeDns("https://cdn.example.com/image.png")).isTrue();
    }

    @Test
    void acceptsUrlWithTrailingDotHostAndUppercaseScheme() throws UnknownHostException {
        givenDnsHost("example.com", "93.184.216.34");

        assertThat(validateWithFakeDns("HTTPS://EXAMPLE.com./image.png")).isTrue();
    }

    @Test
    void rejectsUrlWhenDnsResolvesToPrivateAddress() throws UnknownHostException {
        givenDnsHost("rebind.example.com", "10.0.0.5");

        assertThat(validateWithFakeDns("http://rebind.example.com/image.png")).isFalse();
    }

    @Test
    void rejectsUrlWhenAnyResolvedAddressIsPrivate() throws UnknownHostException {
        givenDnsHost("mixed.example.com", "93.184.216.34", "192.168.0.9");

        assertThat(validateWithFakeDns("http://mixed.example.com/image.png")).isFalse();
    }

    @Test
    void rejectsUrlWhenDnsResolvesToLoopbackIpv6() throws UnknownHostException {
        givenDnsHost("v6.example.com", "::1");

        assertThat(validateWithFakeDns("http://v6.example.com/image.png")).isFalse();
    }

    @Test
    void rejectsUrlWhenHostCannotBeResolved() {
        assertThat(validateWithFakeDns("http://missing.example.com/image.png")).isFalse();
    }

    @Test
    void rejectsReservedAndInternalHostnamesWithoutDnsLookup() {
        assertThat(validateWithFakeDns("http://localhost/image.png")).isFalse();
        assertThat(validateWithFakeDns("http://a.localhost/image.png")).isFalse();
        assertThat(validateWithFakeDns("http://service.local/image.png")).isFalse();
        assertThat(validateWithFakeDns("http://host.docker.internal/image.png")).isFalse();
        assertThat(validateWithFakeDns("http://gateway.docker.internal/image.png")).isFalse();
        assertThat(validateWithFakeDns("http://metadata.google.internal/computeMetadata/v1/")).isFalse();
        assertThat(validateWithFakeDns("http://node1.internal/image.png")).isFalse();
    }

    @Test
    void rejectsLoopbackAndPrivateIpLiterals() {
        // IP 字面量由系统解析器本地归一化，不触发真实 DNS 查询
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://127.0.0.1/image.png")).isFalse();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://10.1.2.3/image.png")).isFalse();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://172.16.0.1/image.png")).isFalse();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://172.31.255.254/image.png")).isFalse();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://192.168.1.1/image.png")).isFalse();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://0.0.0.0/image.png")).isFalse();
    }

    @Test
    void rejectsCloudMetadataAndLinkLocalAddresses() {
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://169.254.169.254/latest/meta-data/"))
                .isFalse();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://[fe80::1]/image.png")).isFalse();
    }

    @Test
    void rejectsIpv6LoopbackAndUniqueLocalLiterals() {
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://[::1]/image.png")).isFalse();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://[fc00::1]/image.png")).isFalse();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://[fd12:3456::1]/image.png")).isFalse();
    }

    @Test
    void rejectsIpv4MappedAndCompatibleIpv6LiteralsPointingAtPrivateSpace() {
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://[::ffff:127.0.0.1]/image.png")).isFalse();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://[::ffff:10.0.0.1]/image.png")).isFalse();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://[::127.0.0.1]/image.png")).isFalse();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://[::192.168.1.1]/image.png")).isFalse();
    }

    @Test
    void rejectsNonCanonicalIpv4LiteralsThatParseAsLoopback() {
        // Java 会把缩写/十六进制写法归一化成对应 IPv4，必须按解析结果校验
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://127.1/image.png")).isFalse();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://0x7f.0.0.1/image.png")).isFalse();
    }

    @Test
    void rejectsNonHttpSchemesAndMalformedUrls() {
        assertThat(validateWithFakeDns("ftp://example.com/image.png")).isFalse();
        assertThat(validateWithFakeDns("file:///etc/passwd")).isFalse();
        assertThat(validateWithFakeDns("gopher://example.com/")).isFalse();
        assertThat(validateWithFakeDns("example.com/image.png")).isFalse();
        assertThat(validateWithFakeDns("data:image/png;base64,AAAA")).isFalse();
        assertThat(validateWithFakeDns("http://")).isFalse();
        assertThat(validateWithFakeDns("http://exa mple.com/image.png")).isFalse();
        assertThat(validateWithFakeDns(" ")).isFalse();
        assertThat(validateWithFakeDns(null)).isFalse();
    }

    @Test
    void acceptsPublicIpLiteralUrlWithoutDnsLookup() {
        // IP 字面量由系统解析器本地归一化，不触发真实 DNS 查询
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://93.184.216.34/image.png")).isTrue();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("https://[2606:2800:220:1:248:1893:25c8:1946]/"))
                .isTrue();
    }

    @Test
    void rejectsTunnelledIpv6EmbeddingPrivateIpv4() {
        // 6to4（2002::/16）嵌入回环/内网 IPv4，在具备 6to4 路由的网络可落地内网（红队 S-4a）
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://[2002:7f00:1::]/image.png")).isFalse();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://[2002:a00:1::]/image.png")).isFalse();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://[2002:c0a8:101::]/image.png")).isFalse();
        // NAT64（64:ff9b::/96）嵌入内网 IPv4
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://[64:ff9b::7f00:1]/image.png")).isFalse();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://[64:ff9b::c0a8:1]/image.png")).isFalse();
    }

    @Test
    void acceptsTunnelledIpv6EmbeddingPublicIpv4() {
        // 嵌入公网 IPv4 的隧道地址按 IPv4 判定放行
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://[2002:5db8:d001::]/image.png")).isTrue();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://[64:ff9b::5db8:d001]/image.png")).isTrue();
    }

    @Test
    void rejectsCgnatSharedAddressSpace() {
        // RFC 6598（100.64.0.0/10）：云元数据（如阿里云 100.100.100.200）与 Tailscale 内网都在此段
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://100.100.100.200/latest/meta-data/"))
                .isFalse();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://100.64.0.1/image.png")).isFalse();
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://100.127.255.254/image.png")).isFalse();
        // 段外仍按原规则处理
        assertThat(PublicHttpUrlValidator.isAllowedPublicHttpUrl("http://101.1.2.3/image.png")).isTrue();
    }
}
