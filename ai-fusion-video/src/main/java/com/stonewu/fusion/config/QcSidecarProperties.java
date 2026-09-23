package com.stonewu.fusion.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI Drama OS 质检 sidecar（ai-drama-qc）的访问配置。
 */
@Component
@ConfigurationProperties(prefix = "app.qc-sidecar")
@Getter
@Setter
public class QcSidecarProperties {

    private String baseUrl = "http://localhost:9900";

    private int connectTimeoutSeconds = 10;

    /** 抽帧与 opencv 分析在长视频上耗时明显，读取超时需远大于普通接口调用 */
    private int readTimeoutSeconds = 300;
}
