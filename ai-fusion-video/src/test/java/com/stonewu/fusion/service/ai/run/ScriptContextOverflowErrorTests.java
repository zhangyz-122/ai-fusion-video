package com.stonewu.fusion.service.ai.run;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptContextOverflowErrorTests {

    @Test
    void matchesVolcengineMaxMessageTokensSignature() {
        RuntimeException failure = new RuntimeException(
                "volcengine HTTP 400: Total tokens of image and text exceed max message tokens");
        assertThat(ScriptContextOverflowError.matches(failure)).isTrue();
    }

    @Test
    void matchesCommonProviderContextLengthSignatures() {
        assertThat(ScriptContextOverflowError.matches(new RuntimeException(
                "This model's maximum context length is 262144 tokens"))).isTrue();
        assertThat(ScriptContextOverflowError.matches(new RuntimeException(
                "prompt is too long: 250000 tokens > 200000 maximum"))).isTrue();
        assertThat(ScriptContextOverflowError.matches(new RuntimeException(
                "context_length_exceeded: the request exceeds the available context window")))
                .isTrue();
    }

    @Test
    void matchesWrappedFailureAlongCauseChain() {
        RuntimeException wrapped = new IllegalStateException(
                "model call failed",
                new RuntimeException("Total tokens exceed max message tokens"));
        assertThat(ScriptContextOverflowError.matches(wrapped)).isTrue();
    }

    @Test
    void ordinaryFailuresDoNotMatch() {
        assertThat(ScriptContextOverflowError.matches(
                new RuntimeException("Connection refused"))).isFalse();
        assertThat(ScriptContextOverflowError.matches(
                new RuntimeException("HTTP 429 rate limit exceeded"))).isFalse();
        assertThat(ScriptContextOverflowError.matches(
                new RuntimeException((String) null))).isFalse();
        assertThat(ScriptContextOverflowError.matches(null)).isFalse();
    }

    @Test
    void terminalMessagePointsToAutoSplitWithoutEpisodes() {
        String message = ScriptContextOverflowError.terminalMessage(false);
        assertThat(message)
                .contains("剧本过长超出模型上下文")
                .contains("自动分块解析（auto-split）");
        assertThat(message).doesNotContain("Total tokens");
    }

    @Test
    void terminalMessageMentionsContinuationWhenEpisodesPersisted() {
        String message = ScriptContextOverflowError.terminalMessage(true);
        assertThat(message)
                .contains("剧本过长超出模型上下文")
                .contains("已保留")
                .contains("/continue")
                .contains("自动分块解析");
    }
}
