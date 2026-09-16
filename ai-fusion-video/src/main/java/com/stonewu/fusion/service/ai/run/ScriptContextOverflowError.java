package com.stonewu.fusion.service.ai.run;

import java.util.Locale;

/**
 * 供应商“上下文/消息 tokens 超限”错误签名识别与终态文案映射（纯函数）。
 * <p>
 * 背景：超长剧本若把整本原文塞进单个工具结果，下一轮模型请求必然超出上下文，
 * 供应商返回 HTTP 400（如实测火山方舟 “Total tokens of image and text exceed
 * max message tokens”）。supervisor 收到 SOURCE_FAILURE 时用本类识别此类错误，
 * 把 error_message 写成人话指引（改用自动分块解析），而不是透传原始英文 400。
 */
public final class ScriptContextOverflowError {

    /**
     * 供应商错误消息中的小写签名片段（包含匹配，容忍措辞变化）。
     * 新接入的供应商报错措辞不同时在此补充。
     */
    private static final String[] SIGNATURES = {
            // 火山方舟 400：Total tokens of image and text exceed max message tokens
            "exceed max message tokens",
            "exceeds max message tokens",
            // OpenAI：This model's maximum context length is N tokens
            "context length",
            "context_length",
            // Anthropic / Cohere：prompt is too long
            "prompt is too long",
            // 通用：上下文窗口措辞
            "context window",
            "contextwindow",
            // Gemini：The input token count exceeds the maximum number of tokens
            "input token count",
            // 通用 tokens 超限措辞
            "too many input tokens",
            "input length exceeds",
    };

    private static final String BASE_MESSAGE = "剧本过长超出模型上下文，请使用自动分块解析（auto-split）";

    private ScriptContextOverflowError() {
    }

    /**
     * 判断 SOURCE_FAILURE 是否为上下文超限类错误。
     * 沿 cause 链向上查找若干层，容忍框架把供应商异常包了一层的情况。
     */
    public static boolean matches(Throwable failure) {
        Throwable current = failure;
        int depth = 0;
        while (current != null && depth < 5) {
            if (messageMatches(current.getMessage())) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }

    /**
     * 终态 error_message 的人话文案。
     *
     * @param episodePersisted 本 run 是否已成功落库过分集；true 时提示已保留、可继续
     */
    public static String terminalMessage(boolean episodePersisted) {
        return episodePersisted
                ? BASE_MESSAGE + "；本次已保存的分集已保留，可继续解析（/continue）或改用自动分块解析"
                : BASE_MESSAGE;
    }

    private static boolean messageMatches(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        for (String signature : SIGNATURES) {
            if (normalized.contains(signature)) {
                return true;
            }
        }
        return false;
    }
}
