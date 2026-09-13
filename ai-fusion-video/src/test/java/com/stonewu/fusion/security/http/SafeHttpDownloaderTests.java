package com.stonewu.fusion.security.http;

import com.stonewu.fusion.common.BusinessException;
import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SW-T18(P0):服务端出站拉取统一安全通道的实测用例。
 * <p>
 * 用本机 JDK HttpServer 模拟远端公网主机（测试注入放行 127.0.0.1 的校验闸门），
 * 真实验证 302 重定向逐跳复检行为，不触外网。
 */
class SafeHttpDownloaderTests {

    private static HttpServer server;
    private static String base;

    private static final Map<String, AtomicInteger> HITS = new ConcurrentHashMap<>();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/final", exchange -> {
            HITS.computeIfAbsent("/final", k -> new AtomicInteger()).incrementAndGet();
            byte[] body = "final-payload".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/hop", exchange -> {
            HITS.computeIfAbsent("/hop", k -> new AtomicInteger()).incrementAndGet();
            exchange.getResponseHeaders().set("Location", base + "/final");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/badhop", exchange -> {
            HITS.computeIfAbsent("/badhop", k -> new AtomicInteger()).incrementAndGet();
            exchange.getResponseHeaders().set("Location", "http://169.254.169.254/latest/meta-data/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/loop", exchange -> {
            HITS.computeIfAbsent("/loop", k -> new AtomicInteger()).incrementAndGet();
            exchange.getResponseHeaders().set("Location", base + "/loop");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/noloc", exchange -> {
            HITS.computeIfAbsent("/noloc", k -> new AtomicInteger()).incrementAndGet();
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    void resetHitCounters() {
        HITS.clear();
    }

    @AfterEach
    void restoreGate() {
        SafeHttpDownloader.gate = PublicHttpUrlValidator::isAllowedPublicHttpUrl;
    }

    /** 模拟 “127.0.0.1 是公网主机”：放行本机地址，其余仍按真实校验。 */
    private void treatLoopbackAsPublic() {
        SafeHttpDownloader.gate = url -> {
            HttpUrl parsed = HttpUrl.parse(url);
            return parsed != null && "127.0.0.1".equals(parsed.host());
        };
    }

    private String fetch(String url) throws IOException {
        return SafeHttpDownloader.fetch(new OkHttpClient(), url, "测试下载",
                target -> new Request.Builder().url(target).get().build(),
                response -> {
                    try (response) {
                        return response.body() == null ? null : response.body().string();
                    }
                });
    }

    @Test
    void followsValidatedRedirectChainToFinalBody() throws IOException {
        treatLoopbackAsPublic();

        assertThat(fetch(base + "/hop")).isEqualTo("final-payload");
        assertThat(HITS.get("/hop").get()).isEqualTo(1);
        assertThat(HITS.get("/final").get()).isEqualTo(1);
    }

    @Test
    void blocksRedirectToLinkLocalMetadataTarget() {
        treatLoopbackAsPublic();

        assertThatThrownBy(() -> fetch(base + "/badhop"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SSRF 防护拒绝");

        // 元数据地址绝不能被实际请求
        assertThat(HITS.getOrDefault("/badhop", new AtomicInteger()).get()).isEqualTo(1);
    }

    @Test
    void failsWhenRedirectsExceedLimit() {
        treatLoopbackAsPublic();

        assertThatThrownBy(() -> fetch(base + "/loop"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("重定向次数过多");

        assertThat(HITS.get("/loop").get()).isEqualTo(SafeHttpDownloader.MAX_REDIRECTS + 1);
    }

    @Test
    void failsWhenRedirectMissingLocation() {
        treatLoopbackAsPublic();

        assertThatThrownBy(() -> fetch(base + "/noloc"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("缺少 Location");
    }

    @Test
    void rejectsInternalInitialUrlByDefaultGateWithoutHittingServer() {
        assertThatThrownBy(() -> fetch(base + "/final"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SSRF 防护拒绝");

        assertThat(HITS.getOrDefault("/final", new AtomicInteger()).get()).isZero();
    }

    @Test
    void requirePublicUrlRejectsLoopbackAndCgnatLiterals() {
        assertThatThrownBy(() -> SafeHttpDownloader.requirePublicUrl(
                "http://127.0.0.1:8080/actuator/env", "诊断 URL"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SSRF 防护拒绝");
        assertThatThrownBy(() -> SafeHttpDownloader.requirePublicUrl(
                "http://100.100.100.200/latest/meta-data/", "元数据 URL"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SSRF 防护拒绝");
    }
}
