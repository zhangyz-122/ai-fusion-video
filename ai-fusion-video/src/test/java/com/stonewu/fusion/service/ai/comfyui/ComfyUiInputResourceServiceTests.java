package com.stonewu.fusion.service.ai.comfyui;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.ai.ApiConfig;
import com.stonewu.fusion.service.ai.comfyui.client.ComfyUiNativeClient;
import com.stonewu.fusion.service.ai.comfyui.client.ComfyUiUploadResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComfyUiInputResourceServiceTests {

    @Mock
    private ComfyUiNativeClient nativeClient;

    @Test
    void uploadImagesDecodesSupportedDataUriBeforeNativeUpload() {
        ComfyUiInputResourceService service = new ComfyUiInputResourceService(nativeClient);
        ApiConfig apiConfig = ApiConfig.builder().id(7L).build();
        byte[] image = "png-data".getBytes(StandardCharsets.UTF_8);
        String source = "data:image/png;base64," + Base64.getEncoder().encodeToString(image);
        when(nativeClient.uploadImage(any(), any(), any(), any(), any()))
                .thenReturn(new ComfyUiUploadResult("task-referenceImages-0.png",
                        "ai-fusion-video", "input"));

        assertThat(service.uploadImages(apiConfig, "task", "referenceImages", List.of(source)))
                .containsExactly("ai-fusion-video/task-referenceImages-0.png");

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(nativeClient).uploadImage(eq(apiConfig), bytes.capture(),
                eq("task-referenceImages-0.png"), eq("image/png"), eq("ai-fusion-video"));
        assertThat(bytes.getValue()).isEqualTo(image);
    }

    @Test
    void uploadAudiosDecodesSupportedDataUriBeforeNativeUpload() {
        ComfyUiInputResourceService service = new ComfyUiInputResourceService(nativeClient);
        ApiConfig apiConfig = ApiConfig.builder().id(7L).build();
        byte[] audio = "mp3-data".getBytes(StandardCharsets.UTF_8);
        String source = "data:audio/mpeg;base64," + Base64.getEncoder().encodeToString(audio);
        when(nativeClient.uploadAudio(any(), any(), any(), any(), any()))
                .thenReturn(new ComfyUiUploadResult("task-referenceAudios-0.mp3",
                        "ai-fusion-video", "input"));

        assertThat(service.uploadAudios(apiConfig, "task", "referenceAudios", List.of(source)))
                .containsExactly("ai-fusion-video/task-referenceAudios-0.mp3");

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(nativeClient).uploadAudio(eq(apiConfig), bytes.capture(),
                eq("task-referenceAudios-0.mp3"), eq("audio/mpeg"), eq("ai-fusion-video"));
        assertThat(bytes.getValue()).isEqualTo(audio);
    }

    @Test
    void uploadAudiosRejectsNonAudioDataUriContentType() {
        ComfyUiInputResourceService service = new ComfyUiInputResourceService(nativeClient);
        ApiConfig apiConfig = ApiConfig.builder().id(7L).build();
        String source = "data:image/png;base64,"
                + Base64.getEncoder().encodeToString("png-data".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() ->
                service.uploadAudios(apiConfig, "task", "referenceAudios", List.of(source)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是受支持的音频");

        verify(nativeClient, never()).uploadAudio(any(), any(), any(), any(), any());
    }

    @Test
    void uploadAudiosDownloadsHttpSourceAndMapsMpegContentTypeToMp3() throws Exception {
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        byte[] audio = "mp3-bytes".getBytes(StandardCharsets.UTF_8);
        server.createContext("/voice.mp3", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(200, audio.length);
            exchange.getResponseBody().write(audio);
            exchange.close();
        });
        server.start();
        try {
            ComfyUiInputResourceService service = new ComfyUiInputResourceService(nativeClient);
            ApiConfig apiConfig = ApiConfig.builder().id(7L).build();
            when(nativeClient.uploadAudio(any(), any(), any(), any(), any()))
                    .thenReturn(new ComfyUiUploadResult(
                            "task-referenceAudios-0.mp3", "ai-fusion-video", "input"));

            assertThat(service.uploadAudios(apiConfig, "task", "referenceAudios",
                    List.of("http://127.0.0.1:" + server.getAddress().getPort() + "/voice.mp3")))
                    .containsExactly("ai-fusion-video/task-referenceAudios-0.mp3");

            ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
            verify(nativeClient).uploadAudio(eq(apiConfig), bytes.capture(),
                    eq("task-referenceAudios-0.mp3"), eq("audio/mpeg"), eq("ai-fusion-video"));
            assertThat(bytes.getValue()).isEqualTo(audio);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readBoundedStopsBeforeWritingPastConfiguredLimit() {
        byte[] body = "123456789".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ComfyUiInputResourceService.readBounded(
                new ByteArrayInputStream(body), 8, "ComfyUI 单张输入图片不能超过 20MB"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过 20MB");
    }
}
