package com.stonewu.fusion.service.production.qc;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.config.QcSidecarProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ai-drama-qc sidecar 的类型化 HTTP 客户端，对应 POST /evaluate 契约。
 */
@Slf4j
@Component
public class QcSidecarClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String evaluateUrl;

    public QcSidecarClient(QcSidecarProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.evaluateUrl = StrUtil.removeSuffix(properties.getBaseUrl(), "/") + "/evaluate";
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(properties.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
    }

    public QcEvaluationResult evaluate(QcEvaluationRequest request) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "质检请求无法序列化: " + e.getMessage());
        }
        Request httpRequest = new Request.Builder()
                .url(evaluateUrl)
                .post(RequestBody.create(payload, JSON_MEDIA_TYPE))
                .build();
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String body = readBody(response.body());
            if (!response.isSuccessful()) {
                log.warn("[qc] sidecar 返回异常状态: code={}, body={}", response.code(), body);
                throw new BusinessException(502, "质检 sidecar 返回 " + response.code());
            }
            return objectMapper.readValue(body, QcEvaluationResult.class);
        } catch (IOException e) {
            throw new BusinessException(502, "质检 sidecar 不可达（" + evaluateUrl + "）: " + e.getMessage());
        }
    }

    private String readBody(ResponseBody body) throws IOException {
        if (body == null) {
            throw new BusinessException(502, "质检 sidecar 返回空响应");
        }
        if (body.contentLength() > MAX_RESPONSE_BYTES) {
            throw new BusinessException(502, "质检 sidecar 响应超过长度上限");
        }
        return body.string();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record QcEvaluationRequest(String videoUrl,
                                      String firstFrameUrl,
                                      String lastFrameUrl,
                                      String prompt,
                                      int expectedCharacterCount,
                                      double expectedDurationSeconds,
                                      List<String> checks) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record QcCheckOutcome(String criterion,
                                 String verdict,
                                 Double valueScore,
                                 Double thresholdValue,
                                 String failureCode,
                                 String evidenceUrl) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record QcEvaluationResult(String overallVerdict,
                                     List<QcCheckOutcome> checks,
                                     long elapsedMs) {
    }
}
