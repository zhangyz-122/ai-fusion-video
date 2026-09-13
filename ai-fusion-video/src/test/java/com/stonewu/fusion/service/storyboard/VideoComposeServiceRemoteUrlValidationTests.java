package com.stonewu.fusion.service.storyboard;

import com.stonewu.fusion.service.storage.MediaStorageService;
import com.stonewu.fusion.service.storage.StorageConfigService;
import com.stonewu.fusion.service.task.TaskStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * SW-T18(P0):VideoComposeService 下载校验与 PublicHttpUrlValidator 统一后的用例。
 * <p>
 * 红队 S-4b：旧自研判定只认 isSiteLocalAddress（fec0::/10 旧站点本地段），
 * 放行现代 ULA fd00::/8 与 IPv4-compatible IPv6；统一后这些地址必须全部被拒。
 */
class VideoComposeServiceRemoteUrlValidationTests {

    private VideoComposeService newService(String allowedHostsConfig) {
        VideoComposeService service = new VideoComposeService(
                null, null,
                mock(MediaStorageService.class),
                mock(StorageConfigService.class),
                mock(TaskStreamService.class),
                Runnable::run);
        ReflectionTestUtils.setField(service, "allowedHostsConfig", allowedHostsConfig);
        return service;
    }

    private void validate(VideoComposeService service, String url) throws Throwable {
        Method method = VideoComposeService.class.getDeclaredMethod("validateRemoteUri", URI.class);
        method.setAccessible(true);
        try {
            method.invoke(service, URI.create(url));
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    void rejectsIpv6UniqueLocalAddresses() {
        // 修复前：fd00::/8 不在旧版 isSiteLocalAddress 的 fec0::/10 范围内，直接放行
        for (String url : List.of(
                "http://[fd00::5]/clip.mp4",
                "http://[fc00::1]/clip.mp4",
                "http://[::127.0.0.1]/clip.mp4",
                "http://[2002:7f00:1::]/clip.mp4")) {
            assertThatThrownBy(() -> validate(newService(""), url))
                    .as("内网地址 %s 必须被拒绝", url)
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("拒绝访问内网或本地地址");
        }
    }

    @Test
    void rejectsLoopbackAndPrivateIpv4Literals() {
        for (String url : List.of(
                "http://127.0.0.1:8123/clip.mp4",
                "http://10.0.0.5/clip.mp4",
                "http://192.168.1.9/clip.mp4",
                "http://169.254.169.254/latest/meta-data/")) {
            assertThatThrownBy(() -> validate(newService(""), url))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("拒绝访问内网或本地地址");
        }
    }

    @Test
    void acceptsPublicIpLiteralUrl() {
        // IP 字面量本地归一化，不触发真实 DNS
        assertThatCode(() -> validate(newService(""), "http://93.184.216.34/clip.mp4"))
                .doesNotThrowAnyException();
    }

    @Test
    void allowlistedHostsStillTakePrecedenceWithoutIpChecks() {
        // 管理员显式配置的白名单行为保持不变
        assertThatCode(() -> validate(newService("*.example.com,127.0.0.1"),
                "http://cdn.example.com/clip.mp4"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validate(newService("*.example.com,127.0.0.1"),
                "http://evil.example.net/clip.mp4"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("不在白名单");
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThatThrownBy(() -> validate(newService(""), "file:///etc/passwd"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("仅允许下载 http/https");
    }
}
