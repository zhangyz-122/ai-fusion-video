package com.stonewu.fusion.service.ai.comfyui;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.ai.ApiConfig;
import com.stonewu.fusion.service.ai.comfyui.client.ComfyUiNativeClient;
import com.stonewu.fusion.service.ai.comfyui.client.ComfyUiUploadResult;
import okhttp3.OkHttpClient;
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
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void downloadHttpRejectsLoopbackInternalAndMetadataUrls() {
        ComfyUiInputResourceService service = new ComfyUiInputResourceService(nativeClient);
        ApiConfig apiConfig = ApiConfig.builder().id(7L).build();

        for (String url : List.of(
                "http://127.0.0.1:9200/_cat/indices",
                "http://169.254.169.254/latest/meta-data/",
                "http://100.100.100.200/latest/meta-data/",
                "http://192.168.1.9/image.png",
                "http://10.0.0.5/image.png",
                "http://[fd00::5]/image.png")) {
            assertThatThrownBy(() -> service.uploadImages(apiConfig, "task", "referenceImages", List.of(url)))
                    .as("图片输入 %s 必须被 SSRF 防护拒绝", url)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("SSRF 防护拒绝");
        }
        verifyNoInteractions(nativeClient);
    }

    @Test
    void downloadVideoHttpRejectsLoopbackInternalAndMetadataUrls() {
        ComfyUiInputResourceService service = new ComfyUiInputResourceService(nativeClient);
        ApiConfig apiConfig = ApiConfig.builder().id(7L).build();

        for (String url : List.of(
                "http://127.0.0.1:8081/view?filename=clip.mp4",
                "http://172.16.0.9/clip.mp4",
                "http://100.100.100.200/latest/meta-data/",
                "http://localhost/clip.mp4")) {
            assertThatThrownBy(() -> service.uploadVideos(apiConfig, "task", "referenceVideos", List.of(url)))
                    .as("视频输入 %s 必须被 SSRF 防护拒绝", url)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("SSRF 防护拒绝");
        }
        verifyNoInteractions(nativeClient);
    }

    @Test
    void baseClientDoesNotFollowRedirects() throws Exception {
        // 红队 S-1：首跳校验后经 302 跳向内网的绕过，必须通过关闭自动重定向阻断
        var field = ComfyUiInputResourceService.class.getDeclaredField("baseClient");
        field.setAccessible(true);
        OkHttpClient client = (OkHttpClient) field.get(new ComfyUiInputResourceService(nativeClient));

        assertThat(client.followRedirects()).isFalse();
        assertThat(client.followSslRedirects()).isFalse();
    }
}
