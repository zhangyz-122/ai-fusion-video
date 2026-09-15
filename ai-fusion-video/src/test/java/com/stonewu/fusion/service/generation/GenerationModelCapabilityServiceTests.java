package com.stonewu.fusion.service.generation;

import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.generation.ImageTask;
import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.service.ai.ApiConfigService;
import com.stonewu.fusion.service.ai.ModelPresetService;
import com.stonewu.fusion.service.ai.model.AiModelMetadataResolver;
import com.stonewu.fusion.service.storage.StorageConfigService;
import com.stonewu.fusion.service.system.PresetArtStyleResourceResolver;
import com.stonewu.fusion.service.system.SystemConfigService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerationModelCapabilityServiceTests {

        private final ModelPresetService presetService = createPresetService();
        private final AiModelMetadataResolver metadataResolver = new AiModelMetadataResolver(mock(ApiConfigService.class));
        private final GenerationModelCapabilityService service = new GenerationModelCapabilityService(metadataResolver, presetService);

        private static ModelPresetService createPresetService() {
                ModelPresetService presetService = new ModelPresetService();
                presetService.init();
                return presetService;
        }

    @Test
    void shouldUsePresetReferenceImageCapabilityForSupportedOpenAiImageModels() {
        List<String> supportedCodes = List.of("gpt-image-1", "gpt-image-1.5", "gpt-image-1-mini", "gpt-image-2");

        supportedCodes.forEach(modelCode -> {
            AiModel model = AiModel.builder()
                    .name(modelCode)
                    .code(modelCode)
                    .capabilityPresetCode(modelCode)
                    .build();

            GenerationModelCapabilityService.ImageModelCapability capability = service.resolveImageCapability(model, "openai");

            assertTrue(capability.supportsReferenceImages(), modelCode);
            assertEquals(0, capability.minReferenceImages(), modelCode);
            assertEquals(16, capability.maxReferenceImages(), modelCode);
        });
    }

    @Test
    void shouldAllowReferenceImagesForPresetSupportedOpenAiImageModel() {
        AiModel model = AiModel.builder()
                .name("GPT Image 1")
                .code("gpt-image-1")
                .capabilityPresetCode("gpt-image-1")
                .build();
        ImageTask task = ImageTask.builder()
                .refImageUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.png")))
                .build();

        service.validateImageTask(model, task, "openai");
    }

    @Test
    void shouldExposeOfficialOpenAiImagePresetSizesAndRatios() {
        AiModel gptImage1 = AiModel.builder()
                .name("GPT Image 1")
                .code("gpt-image-1")
                .capabilityPresetCode("gpt-image-1")
                .build();
        var gptImage1Config = service.getMergedModelConfig(gptImage1);

        assertEquals(List.of("1:1", "2:3", "3:2"),
                JSONUtil.toList(gptImage1Config.getJSONArray("supportedAspectRatios"), String.class));
        assertEquals("1536x1024",
                gptImage1Config.getJSONObject("supportedSizes").getJSONObject("standard").getStr("3:2"));

        AiModel gptImage2 = AiModel.builder()
                .name("GPT Image 2")
                .code("gpt-image-2")
                .capabilityPresetCode("gpt-image-2")
                .build();
        var gptImage2Config = service.getMergedModelConfig(gptImage2);

        assertEquals(List.of("auto", "1:1", "3:2", "2:3", "4:3", "3:4", "5:4", "4:5",
                        "16:9", "9:16", "2:1", "1:2", "21:9", "9:21"),
                JSONUtil.toList(gptImage2Config.getJSONArray("supportedAspectRatios"), String.class));
        assertFalse(gptImage2Config.getBool("asyncMode"));
        assertTrue(gptImage2Config.getBool("supportCustomSize"));
        assertEquals(16, gptImage2Config.getInt("sizeMultiple"));
        assertEquals(3840, gptImage2Config.getInt("maxEdge"));
        assertEquals(3, gptImage2Config.getInt("maxAspectRatio"));
        assertEquals(655360, gptImage2Config.getInt("minPixels"));
        assertEquals(8294400, gptImage2Config.getInt("maxPixels"));
        assertEquals("2048x1152",
                gptImage2Config.getJSONObject("supportedSizes").getJSONObject("2K").getStr("16:9"));
        assertEquals("2048x1360",
                gptImage2Config.getJSONObject("supportedSizes").getJSONObject("2K").getStr("3:2"));
        assertEquals("2160x3840",
                gptImage2Config.getJSONObject("supportedSizes").getJSONObject("4K").getStr("9:16"));
        assertEquals("3840x1648",
                gptImage2Config.getJSONObject("supportedSizes").getJSONObject("4K").getStr("21:9"));
    }

    @Test
    void shouldExposeAgnesImage21PresetCapabilities() {
        AiModel model = AiModel.builder()
                .name("Agnes Image 2.1 Flash")
                .code("agnes-image-2.1-flash")
                .capabilityPresetCode("agnes-image-2.1-flash")
                .build();

        GenerationModelCapabilityService.ImageModelCapability capability =
                service.resolveImageCapability(model, "openai_compatible");
        var config = service.getMergedModelConfig(model);

        assertTrue(capability.supportsReferenceImages());
        assertEquals(16, capability.maxReferenceImages());
        assertEquals("agnes", config.getStr("imageProtocol"));
        assertFalse(config.containsKey("agnesSizeMode"));
        assertTrue(config.getBool("supportDataUriInput"));
        assertEquals(List.of("url", "data_uri"),
                JSONUtil.toList(config.getJSONArray("referenceImageInputFormats"), String.class));
        assertEquals(List.of("1K", "2K", "3K", "4K"),
                JSONUtil.toList(config.getJSONArray("supportedResolutions"), String.class));
        assertEquals("2624x1472",
                config.getJSONObject("supportedSizes").getJSONObject("2K").getStr("16:9"));
        assertEquals("6272x2688",
                config.getJSONObject("supportedSizes").getJSONObject("4K").getStr("21:9"));
    }

    @Test
    void shouldExposeAgnesImage20DataUriPresetCapabilities() {
        AiModel model = AiModel.builder()
                .name("Agnes Image 2.0 Flash")
                .code("agnes-image-2.0-flash")
                .capabilityPresetCode("agnes-image-2.0-flash")
                .build();

        GenerationModelCapabilityService.ImageModelCapability capability =
                service.resolveImageCapability(model, "openai_compatible");
        var config = service.getMergedModelConfig(model);

        assertTrue(capability.supportsReferenceImages());
        assertEquals(16, capability.maxReferenceImages());
        assertEquals("agnes", config.getStr("imageProtocol"));
        assertFalse(config.containsKey("agnesSizeMode"));
        assertTrue(config.getBool("supportDataUriInput"));
        assertEquals(List.of("url", "data_uri"),
                JSONUtil.toList(config.getJSONArray("referenceImageInputFormats"), String.class));
        assertEquals("1024x768",
                config.getJSONObject("supportedSizes").getJSONObject("standard").getStr("4:3"));
    }

    @Test
    void shouldRejectUrlOnlyLocalReferenceWithoutPublicAccessAtSubmissionValidation() {
        ModelPresetService urlOnlyPresetService = new ModelPresetService() {
            @Override
            public String getPresetConfig(String code) {
                return """
                        {
                          "supportReferenceImages": true,
                          "maxReferenceImages": 1,
                          "referenceImageInputFormats": ["url"]
                        }
                        """;
            }
        };
        SystemConfigService systemConfigService = mock(SystemConfigService.class);
        when(systemConfigService.resolvePublicUrl("/media/reference.png")).thenReturn(null);
        ReferenceImageTransportService transportService = new ReferenceImageTransportService(
                mock(StorageConfigService.class),
                systemConfigService,
                mock(PresetArtStyleResourceResolver.class));
        GenerationModelCapabilityService strictService = new GenerationModelCapabilityService(
                metadataResolver, urlOnlyPresetService, transportService);
        AiModel model = AiModel.builder()
                .name("URL Only Image")
                .code("url-only-image")
                .capabilityPresetCode("url-only-image")
                .build();
        ImageTask task = ImageTask.builder()
                .refImageUrls(JSONUtil.toJsonStr(List.of("/media/reference.png")))
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> strictService.validateImageTask(model, task, "openai_compatible"));

        assertTrue(ex.getMessage().contains("未配置访问域名或公网对象存储"));
    }

    @Test
    void shouldUsePresetCapabilityWhenModelConfigDoesNotContainNewFields() {
        GenerationModelCapabilityService serviceWithPreset = new GenerationModelCapabilityService(metadataResolver, new ModelPresetService() {
            @Override
            public String getPresetConfig(String code) {
                if (!"doubao-seedream-3-0-t2i-250415".equals(code)) {
                    return null;
                }
                return """
                        {
                          "supportReferenceImages": false,
                          "minReferenceImages": 0,
                          "maxReferenceImages": 0
                        }
                        """;
            }
        });

        AiModel model = AiModel.builder()
                .name("Seedream 3.0")
                .code("doubao-seedream-3-0-t2i-250415")
                .capabilityPresetCode("doubao-seedream-3-0-t2i-250415")
                .config("{\"defaultWidth\":2048,\"defaultHeight\":2048}")
                .build();

        GenerationModelCapabilityService.ImageModelCapability capability = serviceWithPreset.resolveImageCapability(model, "volcengine");

        assertFalse(capability.supportsReferenceImages());
        assertEquals(0, capability.maxReferenceImages());
    }

    @Test
    void shouldRejectFirstFrameForT2vGoogleFlowModel() {
        AiModel model = AiModel.builder()
                .name("Veo T2V Fast")
                .code("veo_3_1_t2v_fast")
                .capabilityPresetCode("veo_3_1_t2v_fast")
                .build();
        VideoTask task = VideoTask.builder()
                .firstFrameImageUrl("https://example.com/first.png")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateVideoTask(model, task, "GoogleFlowReverseApi"));

        assertTrue(ex.getMessage().contains("不支持首帧图输入"));
    }

    @Test
    void shouldRequireTwoImagesForInterpolationModel() {
        AiModel model = AiModel.builder()
                .name("Interpolation Lite")
                .code("veo_3_1_interpolation_lite")
                .capabilityPresetCode("veo_3_1_interpolation_lite")
                .build();
        VideoTask task = VideoTask.builder()
                .firstFrameImageUrl("https://example.com/first.png")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateVideoTask(model, task, "GoogleFlowReverseApi"));

        assertTrue(ex.getMessage().contains("至少需要 2 张图片输入"));
    }

    @Test
    void shouldRejectLastFrameForSeedanceProFast() {
        AiModel model = AiModel.builder()
                .name("Seedance 1.0 Pro Fast")
                .code("doubao-seedance-1-0-pro-fast-251015")
                .capabilityPresetCode("doubao-seedance-1-0-pro-fast-251015")
                .build();
        VideoTask task = VideoTask.builder()
                .lastFrameImageUrl("https://example.com/last.png")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateVideoTask(model, task, "volcengine"));

        assertTrue(ex.getMessage().contains("不支持尾帧图输入"));
    }

    @Test
    void shouldAllowSeedance20ReferenceMedia() {
        AiModel model = AiModel.builder()
                .name("Seedance 2.0")
                .code("doubao-seedance-2-0-260128")
                .capabilityPresetCode("doubao-seedance-2-0-260128")
                .build();
        VideoTask task = VideoTask.builder()
                .referenceImageUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref-1.png")))
                .referenceVideoUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref-1.mp4")))
                .referenceAudioUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref-1.mp3")))
                .build();

        service.validateVideoTask(model, task, "volcengine");
    }

    @Test
    void shouldRejectMixedSeedance20FrameAndReferenceModes() {
        AiModel model = AiModel.builder()
                .name("Seedance 2.0")
                .code("doubao-seedance-2-0-260128")
                .capabilityPresetCode("doubao-seedance-2-0-260128")
                .build();
        VideoTask task = VideoTask.builder()
                .firstFrameImageUrl("https://example.com/first.png")
                .referenceVideoUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.mp4")))
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateVideoTask(model, task, "volcengine"));

        assertTrue(ex.getMessage().contains("互斥"));
    }

    @Test
    void shouldRejectSeedance20AudioOnlyReferenceMode() {
        AiModel model = AiModel.builder()
                .name("Seedance 2.0")
                .code("doubao-seedance-2-0-260128")
                .capabilityPresetCode("doubao-seedance-2-0-260128")
                .build();
        VideoTask task = VideoTask.builder()
                .referenceAudioUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.mp3")))
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateVideoTask(model, task, "volcengine"));

        assertTrue(ex.getMessage().contains("参考图片或参考视频"));
    }

    @Test
    void shouldAllowDashScopeWanImageReferenceImages() {
        AiModel model = AiModel.builder()
                .name("Wan 2.7 Image")
                .code("wan2.7-image")
                .capabilityPresetCode("wan2.7-image")
                .build();
        ImageTask task = ImageTask.builder()
                .refImageUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.png")))
                .build();

        GenerationModelCapabilityService.ImageModelCapability capability = service.resolveImageCapability(model, "dashscope");

        assertTrue(capability.supportsReferenceImages());
        service.validateImageTask(model, task, "dashscope");
    }

    @Test
    void shouldAllowDashScopeQwenImage20ReferenceImages() {
        AiModel model = AiModel.builder()
                .name("Qwen Image 2.0")
                .code("qwen-image-2.0")
                .capabilityPresetCode("qwen-image-2.0")
                .build();
        ImageTask task = ImageTask.builder()
                .refImageUrls(JSONUtil.toJsonStr(List.of(
                        "https://example.com/ref-1.png",
                        "https://example.com/ref-2.png")))
                .build();

        GenerationModelCapabilityService.ImageModelCapability capability = service.resolveImageCapability(model, "dashscope");

        assertTrue(capability.supportsReferenceImages());
        assertEquals(2, capability.maxReferenceImages());
        service.validateImageTask(model, task, "dashscope");
    }

    @Test
    void shouldRejectFirstFrameForDashScopeT2vModel() {
        AiModel model = AiModel.builder()
                .name("Wan 2.7 T2V")
                .code("wan2.7-t2v")
                .capabilityPresetCode("wan2.7-t2v")
                .build();
        VideoTask task = VideoTask.builder()
                .firstFrameImageUrl("https://example.com/first.png")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateVideoTask(model, task, "dashscope"));

        assertTrue(ex.getMessage().contains("不支持首帧图输入"));
    }

    @Test
    void shouldAllowReferenceAudioForDashScopeT2vModel() {
        AiModel model = AiModel.builder()
                .name("Wan 2.7 T2V")
                .code("wan2.7-t2v")
                .capabilityPresetCode("wan2.7-t2v")
                .build();
        VideoTask task = VideoTask.builder()
                .referenceAudioUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.mp3")))
                .build();

        GenerationModelCapabilityService.VideoModelCapability capability = service.resolveVideoCapability(model, "dashscope");

        assertTrue(capability.supportsReferenceAudios());
        service.validateVideoTask(model, task, "dashscope");
    }

    @Test
    void shouldAllowFirstAndLastFrameForDashScopeI2vModel() {
        AiModel model = AiModel.builder()
                .name("Wan 2.7 I2V")
                .code("wan2.7-i2v")
                .capabilityPresetCode("wan2.7-i2v")
                .build();
        VideoTask task = VideoTask.builder()
                .firstFrameImageUrl("https://example.com/first.png")
                .lastFrameImageUrl("https://example.com/last.png")
                .build();

        GenerationModelCapabilityService.VideoModelCapability capability = service.resolveVideoCapability(model, "dashscope");

        assertTrue(capability.supportsFirstFrame());
        assertTrue(capability.supportsLastFrame());
        service.validateVideoTask(model, task, "dashscope");
    }

    @Test
    void shouldAllowReferenceMediaForDashScopeR2vModel() {
        AiModel model = AiModel.builder()
                .name("Wan 2.7 R2V")
                .code("wan2.7-r2v")
                .capabilityPresetCode("wan2.7-r2v")
                .build();
        VideoTask task = VideoTask.builder()
                .referenceImageUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.png")))
                .referenceVideoUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.mp4")))
                .referenceAudioUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.mp3")))
                .build();

        service.validateVideoTask(model, task, "dashscope");
    }

    @Test
    void shouldAllowDashScopeVideoEditReferenceImageAndVideo() {
        AiModel model = AiModel.builder()
                .name("Wan 2.7 Video Edit")
                .code("wan2.7-videoedit")
                .capabilityPresetCode("wan2.7-videoedit")
                .build();
        VideoTask task = VideoTask.builder()
                .referenceImageUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.png")))
                .referenceVideoUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.mp4")))
                .build();

        GenerationModelCapabilityService.VideoModelCapability capability = service.resolveVideoCapability(model, "dashscope");

        assertTrue(capability.supportsReferenceImages());
        assertTrue(capability.supportsReferenceVideos());
        assertFalse(capability.supportsReferenceAudios());
        service.validateVideoTask(model, task, "dashscope");
    }

    @Test
    void shouldNotInferCapabilitiesFromModelCodeOrProtocol() {
        AiModel model = AiModel.builder()
                .name("Agnes Video without explicit capability preset")
                .code("agnes-video-v2.0")
                .modelProtocol("agnes")
                .build();

        GenerationModelCapabilityService.VideoModelCapability capability =
                service.resolveVideoCapability(model, "openai_compatible");

        assertFalse(capability.supportsFirstFrame());
        assertFalse(capability.supportsLastFrame());
        assertFalse(capability.supportsReferenceImages());
        assertFalse(capability.supportsReferenceVideos());
        assertFalse(capability.supportsReferenceAudios());
    }

    private AiModel buildMultiRefVideoModel(String configJson) {
        return AiModel.builder()
                .name("MultiRef Video")
                .code("multiref_video_model")
                .config(configJson)
                .build();
    }

    private static final String MULTI_REF_CONFIG = """
            {
              "supportFirstFrame": false,
              "supportLastFrame": false,
              "supportReferenceImages": true,
              "maxReferenceImages": 9,
              "supportReferenceVideos": true,
              "maxReferenceVideos": 3,
              "supportReferenceAudios": true,
              "maxReferenceAudios": 3,
              "minImageInputs": 1,
              "maxReferenceTotal": 12
            }
            """;

    @Test
    void shouldExposeReferenceTotalLimitFromModelConfig() {
        AiModel model = buildMultiRefVideoModel(MULTI_REF_CONFIG);

        GenerationModelCapabilityService.VideoModelCapability capability =
                service.resolveVideoCapability(model, "comfyui");

        assertEquals(12, capability.maxReferenceTotal());
        assertEquals(9, capability.maxReferenceImages());
        assertEquals(3, capability.maxReferenceVideos());
        assertEquals(3, capability.maxReferenceAudios());
    }

    @Test
    void shouldRejectReferenceInputsOverTotalLimit() {
        AiModel model = buildMultiRefVideoModel(MULTI_REF_CONFIG);
        VideoTask task = VideoTask.builder()
                .referenceImageUrls(JSONUtil.toJsonStr(List.of(
                        "https://example.com/ref-1.png",
                        "https://example.com/ref-2.png",
                        "https://example.com/ref-3.png",
                        "https://example.com/ref-4.png",
                        "https://example.com/ref-5.png",
                        "https://example.com/ref-6.png",
                        "https://example.com/ref-7.png",
                        "https://example.com/ref-8.png",
                        "https://example.com/ref-9.png")))
                .referenceVideoUrls(JSONUtil.toJsonStr(List.of(
                        "https://example.com/ref-1.mp4",
                        "https://example.com/ref-2.mp4")))
                .referenceAudioUrls(JSONUtil.toJsonStr(List.of(
                        "https://example.com/ref-1.mp3",
                        "https://example.com/ref-2.mp3")))
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateVideoTask(model, task, "comfyui"));

        assertTrue(ex.getMessage().contains("参考素材总数"));
        assertTrue(ex.getMessage().contains("最多 12 个"));
    }

    @Test
    void shouldAllowReferenceInputsExactlyAtTotalLimit() {
        AiModel model = buildMultiRefVideoModel(MULTI_REF_CONFIG);
        VideoTask task = VideoTask.builder()
                .referenceImageUrls(JSONUtil.toJsonStr(List.of(
                        "https://example.com/ref-1.png",
                        "https://example.com/ref-2.png",
                        "https://example.com/ref-3.png",
                        "https://example.com/ref-4.png",
                        "https://example.com/ref-5.png",
                        "https://example.com/ref-6.png",
                        "https://example.com/ref-7.png",
                        "https://example.com/ref-8.png",
                        "https://example.com/ref-9.png")))
                .referenceVideoUrls(JSONUtil.toJsonStr(List.of(
                        "https://example.com/ref-1.mp4",
                        "https://example.com/ref-2.mp4",
                        "https://example.com/ref-3.mp4")))
                .build();

        service.validateVideoTask(model, task, "comfyui");
    }

    @Test
    void shouldStillRejectReferenceInputsOverPerTypeLimit() {
        AiModel model = buildMultiRefVideoModel(MULTI_REF_CONFIG);
        VideoTask task = VideoTask.builder()
                .referenceImageUrls(JSONUtil.toJsonStr(List.of(
                        "https://example.com/ref-1.png",
                        "https://example.com/ref-2.png",
                        "https://example.com/ref-3.png",
                        "https://example.com/ref-4.png",
                        "https://example.com/ref-5.png",
                        "https://example.com/ref-6.png",
                        "https://example.com/ref-7.png",
                        "https://example.com/ref-8.png",
                        "https://example.com/ref-9.png",
                        "https://example.com/ref-10.png")))
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateVideoTask(model, task, "comfyui"));

        assertTrue(ex.getMessage().contains("最多支持 9 张 referenceImageUrls"));
    }

    @Test
    void shouldNotLimitReferenceTotalWhenUnconfigured() {
        AiModel model = buildMultiRefVideoModel("""
                {
                  "supportFirstFrame": false,
                  "supportLastFrame": false,
                  "supportReferenceImages": true,
                  "maxReferenceImages": 9,
                  "supportReferenceVideos": true,
                  "maxReferenceVideos": 3,
                  "supportReferenceAudios": true,
                  "maxReferenceAudios": 3,
                  "minImageInputs": 1
                }
                """);
        VideoTask task = VideoTask.builder()
                .referenceImageUrls(JSONUtil.toJsonStr(List.of(
                        "https://example.com/ref-1.png",
                        "https://example.com/ref-2.png",
                        "https://example.com/ref-3.png",
                        "https://example.com/ref-4.png",
                        "https://example.com/ref-5.png",
                        "https://example.com/ref-6.png",
                        "https://example.com/ref-7.png",
                        "https://example.com/ref-8.png",
                        "https://example.com/ref-9.png")))
                .referenceVideoUrls(JSONUtil.toJsonStr(List.of(
                        "https://example.com/ref-1.mp4",
                        "https://example.com/ref-2.mp4",
                        "https://example.com/ref-3.mp4")))
                .referenceAudioUrls(JSONUtil.toJsonStr(List.of(
                        "https://example.com/ref-1.mp3",
                        "https://example.com/ref-2.mp3",
                        "https://example.com/ref-3.mp3")))
                .build();

        GenerationModelCapabilityService.VideoModelCapability capability =
                service.resolveVideoCapability(model, "comfyui");

        assertNull(capability.maxReferenceTotal());
        service.validateVideoTask(model, task, "comfyui");
    }

    private static final String TIMED_VIDEO_CONFIG = """
            {
              "supportFirstFrame": false,
              "supportLastFrame": false,
              "supportReferenceImages": true,
              "maxReferenceImages": 1,
              "minImageInputs": 1,
              "minDuration": 4,
              "maxDuration": 15,
              "supportedResolutions": ["480p"],
              "supportedAspectRatios": ["16:9"]
            }
            """;

    @Test
    void shouldRejectVideoDurationShorterThanConfiguredMinAtSubmitValidation() {
        AiModel model = buildMultiRefVideoModel(TIMED_VIDEO_CONFIG);
        VideoTask task = VideoTask.builder()
                .prompt("一段空镜头")
                .referenceImageUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.png")))
                .duration(3)
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateVideoTask(model, task, "comfyui"));

        assertTrue(ex.getMessage().contains("最短支持 4 秒"));
    }

    @Test
    void shouldRejectVideoDurationLongerThanConfiguredMaxAtSubmitValidation() {
        AiModel model = buildMultiRefVideoModel(TIMED_VIDEO_CONFIG);
        VideoTask task = VideoTask.builder()
                .prompt("一段空镜头")
                .referenceImageUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.png")))
                .duration(16)
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateVideoTask(model, task, "comfyui"));

        assertTrue(ex.getMessage().contains("最长支持 15 秒"));
    }

    @Test
    void shouldAllowVideoDurationInsideConfiguredRange() {
        AiModel model = buildMultiRefVideoModel(TIMED_VIDEO_CONFIG);
        VideoTask task = VideoTask.builder()
                .prompt("一段空镜头")
                .referenceImageUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.png")))
                .duration(5)
                .build();

        service.validateVideoTask(model, task, "comfyui");
    }

    @Test
    void shouldNotCheckVideoDurationWhenUnconfigured() {
        AiModel model = buildMultiRefVideoModel(MULTI_REF_CONFIG);
        VideoTask task = VideoTask.builder()
                .prompt("一段空镜头")
                .referenceImageUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.png")))
                .duration(999)
                .build();

        service.validateVideoTask(model, task, "comfyui");
    }

    @Test
    void shouldRejectUnsupportedVideoAspectRatioAtSubmitValidation() {
        AiModel model = buildMultiRefVideoModel(TIMED_VIDEO_CONFIG);
        VideoTask task = VideoTask.builder()
                .prompt("一段空镜头")
                .referenceImageUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.png")))
                .duration(5)
                .ratio("4:3")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateVideoTask(model, task, "comfyui"));

        assertTrue(ex.getMessage().contains("不支持画幅 4:3"));
        assertTrue(ex.getMessage().contains("16:9"));
    }

    @Test
    void shouldAllowSupportedVideoAspectRatioIgnoringCase() {
        AiModel model = buildMultiRefVideoModel(TIMED_VIDEO_CONFIG);
        VideoTask task = VideoTask.builder()
                .prompt("一段空镜头")
                .referenceImageUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.png")))
                .duration(5)
                .ratio("16:9 ")
                .build();

        service.validateVideoTask(model, task, "comfyui");
    }

    @Test
    void shouldRejectUnsupportedVideoResolutionAtSubmitValidation() {
        AiModel model = buildMultiRefVideoModel(TIMED_VIDEO_CONFIG);
        VideoTask task = VideoTask.builder()
                .prompt("一段空镜头")
                .referenceImageUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.png")))
                .duration(5)
                .resolution("1080P")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateVideoTask(model, task, "comfyui"));

        assertTrue(ex.getMessage().contains("不支持分辨率 1080P"));
    }

    @Test
    void shouldAllowSupportedVideoResolutionIgnoringCase() {
        AiModel model = buildMultiRefVideoModel(TIMED_VIDEO_CONFIG);
        VideoTask task = VideoTask.builder()
                .prompt("一段空镜头")
                .referenceImageUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.png")))
                .duration(5)
                .resolution("480p")
                .build();

        service.validateVideoTask(model, task, "comfyui");
    }

    @Test
    void shouldRejectVideoTaskWithoutAnyInputAtSubmitValidation() {
        AiModel model = buildMultiRefVideoModel("""
                {
                  "supportFirstFrame": false,
                  "supportLastFrame": false,
                  "supportReferenceImages": true,
                  "maxReferenceImages": 2
                }
                """);
        VideoTask task = VideoTask.builder()
                .duration(5)
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateVideoTask(model, task, "comfyui"));

        assertTrue(ex.getMessage().contains("至少需要提示词、图片、参考视频或参考音频其中一种输入"));
    }

    @Test
    void shouldAllowAudioOnlyVideoTaskWithoutPrompt() {
        AiModel model = buildMultiRefVideoModel("""
                {
                  "supportFirstFrame": false,
                  "supportLastFrame": false,
                  "supportReferenceImages": false,
                  "supportReferenceAudios": true,
                  "maxReferenceAudios": 1
                }
                """);
        VideoTask task = VideoTask.builder()
                .referenceAudioUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.mp3")))
                .duration(5)
                .build();

        service.validateVideoTask(model, task, "comfyui");
    }

    @Test
    void shouldAllowVideoTaskWithImageInputButNoPrompt() {
        AiModel model = buildMultiRefVideoModel(TIMED_VIDEO_CONFIG);
        VideoTask task = VideoTask.builder()
                .referenceImageUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.png")))
                .duration(5)
                .build();

        service.validateVideoTask(model, task, "comfyui");
    }

    @Test
    void shouldRejectUnsupportedImageResolutionAtSubmitValidation() {
        ModelPresetService timedPresetService = new ModelPresetService() {
            @Override
            public String getPresetConfig(String code) {
                return """
                        {
                          "supportReferenceImages": true,
                          "maxReferenceImages": 1,
                          "supportedAspectRatios": ["1:1", "16:9"],
                          "supportedResolutions": ["1K", "2K"]
                        }
                        """;
            }
        };
        GenerationModelCapabilityService imageService = new GenerationModelCapabilityService(
                metadataResolver, timedPresetService);
        AiModel model = AiModel.builder()
                .name("Timed Image")
                .code("timed_image_model")
                .capabilityPresetCode("timed_image_model")
                .build();
        ImageTask task = ImageTask.builder()
                .resolution("4K")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> imageService.validateImageTask(model, task, "openai_compatible"));

        assertTrue(ex.getMessage().contains("图片模型"));
        assertTrue(ex.getMessage().contains("不支持分辨率 4K"));
    }

    @Test
    void shouldAllowSupportedImageAspectRatioIgnoringCase() {
        ModelPresetService timedPresetService = new ModelPresetService() {
            @Override
            public String getPresetConfig(String code) {
                return """
                        {
                          "supportReferenceImages": true,
                          "maxReferenceImages": 1,
                          "supportedAspectRatios": ["1:1", "16:9"]
                        }
                        """;
            }
        };
        GenerationModelCapabilityService imageService = new GenerationModelCapabilityService(
                metadataResolver, timedPresetService);
        AiModel model = AiModel.builder()
                .name("Timed Image")
                .code("timed_image_model")
                .capabilityPresetCode("timed_image_model")
                .build();
        ImageTask task = ImageTask.builder()
                .aspectRatio("16:9")
                .build();

        imageService.validateImageTask(model, task, "openai_compatible");
    }
}
