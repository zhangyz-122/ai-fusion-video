package com.stonewu.fusion.security.http;

import com.stonewu.fusion.common.BusinessException;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.function.Function;

/**
 * 服务端出站 HTTP 拉取的统一安全通道（SSRF 防护，红队 S-1/S-3 修复）。
 * <p>
 * 规则：
 * <ul>
 *   <li>初始 URL 与每一跳重定向目标都必须通过 {@link PublicHttpUrlValidator} 校验；</li>
 *   <li>强制关闭 OkHttp 自动跟随重定向，防止“校验一次、跳转失控”的 302 绕过；</li>
 *   <li>重定向跳数有上限，超限、缺少 Location 或目标非法一律失败。</li>
 * </ul>
 * 调用方通过 {@link RequestFactory} 决定请求细节（Accept 头、方法等），
 * 通过 {@link ResponseHandler} 在响应未重定向时消费响应体。
 */
public final class SafeHttpDownloader {

    /** 重定向最大跳数：合法 CDN 链路一般不超过 3 跳。 */
    static final int MAX_REDIRECTS = 5;

    /** 校验闸门：生产环境固定走 PublicHttpUrlValidator，包内可见以便单测注入固定映射。 */
    @FunctionalInterface
    interface UrlGate {
        boolean isAllowed(String url);
    }

    static UrlGate gate = PublicHttpUrlValidator::isAllowedPublicHttpUrl;

    private SafeHttpDownloader() {
    }

    /**
     * 校验给定 URL 必须是可安全访问的公网 http(s) 地址，否则抛出业务异常。
     */
    public static void requirePublicUrl(String url, String purpose) {
        if (!gate.isAllowed(url)) {
            throw new BusinessException(400, (purpose == null ? "URL" : purpose)
                    + " 指向本机、内网或保留地址，已被 SSRF 防护拒绝");
        }
    }

    /**
     * 以受控方式执行 GET：初始 URL 校验 → 请求 → 30x 时手动解析 Location 并逐跳复检。
     *
     * @param client         基础 OkHttp 客户端（自动重定向状态会被强制覆盖）
     * @param startUrl       初始 URL
     * @param purpose        错误信息中描述用途的前缀，如 “ComfyUI 图片 URL”
     * @param requestFactory 由校验通过的 HttpUrl 构造请求
     * @param handler        消费非重定向响应（读取响应体必须在 handler 内完成）
     * @return handler 的返回值
     */
    public static <T> T fetch(OkHttpClient client,
                              String startUrl,
                              String purpose,
                              Function<HttpUrl, Request> requestFactory,
                              ResponseHandler<T> handler) throws IOException {
        OkHttpClient safeClient = client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
        HttpUrl current = HttpUrl.parse(startUrl);
        if (current == null) {
            throw new BusinessException(400, (purpose == null ? "URL" : purpose) + " 无效");
        }
        for (int hops = 0; hops <= MAX_REDIRECTS; hops++) {
            requirePublicUrl(current.toString(), purpose);
            Request request = requestFactory.apply(current);
            try (Response response = safeClient.newCall(request).execute()) {
                if (isRedirect(response.code())) {
                    String location = response.header("Location");
                    if (location == null || location.isBlank()) {
                        throw new IOException((purpose == null ? "URL" : purpose) + " 重定向缺少 Location");
                    }
                    HttpUrl next = current.resolve(location);
                    if (next == null) {
                        throw new IOException((purpose == null ? "URL" : purpose)
                                + " 重定向地址无效: " + location);
                    }
                    current = next;
                    continue;
                }
                return handler.handle(response);
            }
        }
        throw new IOException((purpose == null ? "URL" : purpose) + " 重定向次数过多（超过 "
                + MAX_REDIRECTS + " 跳）");
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    /**
     * 在响应未被重定向时消费响应体；返回后框架负责关闭响应。
     */
    @FunctionalInterface
    public interface ResponseHandler<T> {
        T handle(Response response) throws IOException;
    }
}
