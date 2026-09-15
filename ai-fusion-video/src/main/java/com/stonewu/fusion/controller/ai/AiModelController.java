package com.stonewu.fusion.controller.ai;

import cn.hutool.json.JSONObject;
import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.common.PageResult;
import com.stonewu.fusion.controller.ai.vo.*;
import com.stonewu.fusion.convert.ai.AiModelConvert;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.service.ai.AiModelService;
import com.stonewu.fusion.service.ai.ModelPresetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.stonewu.fusion.common.CommonResult.success;

@Tag(name = "AI模型管理")
@RestController
@RequestMapping("/api/ai/model")
@RequiredArgsConstructor
@Slf4j
public class AiModelController {

    /** 单模型工具调用探测的独立超时：探测只服务于下拉提示，超时按“未知”处理 */
    private static final long TOOL_CALL_PROBE_TIMEOUT_MILLIS = 3000;
    /** 全部探测的整体 deadline 兜底：正常耗时≈最慢的单模型探测，超时后未完成者按“未知”返回 */
    private static final long TOOL_CALL_PROBE_DEADLINE_MILLIS = 3200;
    /** 探测线程池上限：模型列表数量有限，足以让全部探测并行执行 */
    private static final int TOOL_CALL_PROBE_THREADS = 16;

    /** 探测专用线程池：守护线程不阻塞进程退出，静态共享避免重复创建 */
    private static final ExecutorService TOOL_CALL_PROBE_EXECUTOR =
            Executors.newFixedThreadPool(TOOL_CALL_PROBE_THREADS, probeThreadFactory());

    private static ThreadFactory probeThreadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "ai-model-tool-call-probe-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private final AiModelService aiModelService;
    private final ModelPresetService modelPresetService;

    @PostMapping("/create")
    @Operation(summary = "创建AI模型")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<Long> create(@Valid @RequestBody AiModelCreateReqVO reqVO) {
        AiModel model = AiModel.builder()
                .name(reqVO.getName())
                .code(reqVO.getCode())
                .modelProtocol(reqVO.getModelProtocol())
                .capabilityPresetCode(reqVO.getCapabilityPresetCode())
                .modelType(reqVO.getModelType())
                .icon(reqVO.getIcon()).description(reqVO.getDescription())
                .sort(reqVO.getSort() != null ? reqVO.getSort() : 0)
                .config(reqVO.getConfig())
            .maxConcurrency(reqVO.getMaxConcurrency() != null && reqVO.getMaxConcurrency() > 0
                ? reqVO.getMaxConcurrency() : 5)
                .defaultModel(reqVO.getDefaultModel() != null ? reqVO.getDefaultModel() : false)
            .supportVision(Boolean.TRUE.equals(reqVO.getSupportVision()))
            .multimodalInputTypes(reqVO.getMultimodalInputTypes())
            .multimodalInputTransports(reqVO.getMultimodalInputTransports())
            .supportReasoning(Boolean.TRUE.equals(reqVO.getSupportReasoning()))
            .reasoningEffortLevels(reqVO.getReasoningEffortLevels())
            .contextWindow(reqVO.getContextWindow() != null && reqVO.getContextWindow() > 0
                ? reqVO.getContextWindow() : null)
                .apiConfigId(reqVO.getApiConfigId())
                .comfyuiWorkflowId(reqVO.getComfyuiWorkflowId())
                .build();
        return success(aiModelService.createAiModel(model));
    }

    @PutMapping("/update")
    @Operation(summary = "更新AI模型")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<Boolean> update(@Valid @RequestBody AiModelUpdateReqVO reqVO) {
        aiModelService.updateAiModel(reqVO.getId(), reqVO.getName(), reqVO.getCode(),
                reqVO.getModelProtocol(), reqVO.getCapabilityPresetCode(), reqVO.getModelType(),
                reqVO.getIcon(), reqVO.getDescription(),
                reqVO.getSort(), reqVO.getStatus(), reqVO.getConfig(), reqVO.getDefaultModel(),
                reqVO.getApiConfigId(), reqVO.getMaxConcurrency(), reqVO.getMultimodalInputTypes(),
                reqVO.getMultimodalInputTransports(),
                reqVO.getSupportReasoning(), reqVO.getReasoningEffortLevels(),
                reqVO.getContextWindow(), reqVO.getComfyuiWorkflowId());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除AI模型")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        aiModelService.deleteAiModel(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取AI模型详情")
    @Parameter(name = "id", description = "模型ID", required = true)
    public CommonResult<AiModelRespVO> get(@RequestParam("id") Long id) {
        AiModel model = aiModelService.getById(id);
        return success(model == null ? null : AiModelConvert.INSTANCE.convert(model));
    }

    @GetMapping("/page")
    @Operation(summary = "AI模型分页列表")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<PageResult<AiModelRespVO>> page(@Valid AiModelPageReqVO reqVO) {
        return success(aiModelService.getPage(reqVO.getName(), reqVO.getCode(),
                reqVO.getModelType(), reqVO.getStatus(), reqVO.getPageNo(), reqVO.getPageSize())
                .map(AiModelConvert.INSTANCE::convert));
    }

    @GetMapping("/list")
    @Operation(summary = "获取启用的AI模型列表")
    public CommonResult<List<AiModelRespVO>> list() {
        return success(convertWithToolCallSupport(aiModelService.getEnabledList()));
    }

    @GetMapping("/list-by-type")
    @Operation(summary = "按类型获取AI模型列表")
    @Parameter(name = "type", description = "模型类型", required = true)
    public CommonResult<List<AiModelRespVO>> listByType(@RequestParam("type") Integer type) {
        return success(convertWithToolCallSupport(aiModelService.getListByType(type)));
    }

    /**
     * 下拉列表响应附带工具调用能力标注（supportsToolCalls），
     * 供前端在 Agent 完整解析入口禁用不支持的模型。
     * <p>
     * 探测逐模型并行执行且每个模型带独立超时，整体再加 deadline 兜底：
     * Ollama 离线时列表接口耗时≈单模型超时上限，而不是“模型数 × 超时”的串行求和。
     */
    private List<AiModelRespVO> convertWithToolCallSupport(List<AiModel> models) {
        List<AiModelRespVO> respList = AiModelConvert.INSTANCE.convertList(models);
        if (respList.isEmpty()) {
            return respList;
        }
        List<CompletableFuture<Boolean>> probes = new ArrayList<>(models.size());
        for (AiModel model : models) {
            probes.add(CompletableFuture
                    .supplyAsync(() -> aiModelService.supportsToolCalls(model), TOOL_CALL_PROBE_EXECUTOR)
                    .orTimeout(TOOL_CALL_PROBE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                    .exceptionally(failure -> {
                        log.debug("模型工具调用探测超时或失败，按未知处理: modelId={}, type={}",
                                model.getId(),
                                failure.getCause() == null ? failure.getClass().getSimpleName()
                                        : failure.getCause().getClass().getSimpleName());
                        return null;
                    }));
        }
        try {
            CompletableFuture.allOf(probes.toArray(new CompletableFuture[0]))
                    .get(TOOL_CALL_PROBE_DEADLINE_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException expired) {
            log.warn("模型工具调用探测整体超时，未完成者按未知返回: models={}", models.size());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException unexpected) {
            // 子 future 已用 exceptionally 兜底，allOf 理论上不会抛出；防御性记录
            log.warn("模型工具调用探测聚合异常，未完成者按未知返回", unexpected);
        }
        for (int index = 0; index < respList.size(); index++) {
            respList.get(index).setSupportsToolCalls(probes.get(index).getNow(null));
        }
        return respList;
    }

    @GetMapping("/presets")
    @Operation(summary = "获取模型能力预设列表")
    @Parameter(name = "type", description = "模型类型（可选）")
    public CommonResult<List<JSONObject>> presets(@RequestParam(value = "type", required = false) Integer type) {
        if (type != null) {
            return success(modelPresetService.getPresetsByType(type));
        }
        return success(modelPresetService.getAllPresets());
    }

    @GetMapping("/preset-config")
    @Operation(summary = "获取模型能力预设配置")
    @Parameter(name = "code", description = "能力预设代码", required = true)
    public CommonResult<String> presetConfig(@RequestParam("code") String code) {
        return success(modelPresetService.getPresetConfig(code));
    }

    @PostMapping("/test-text-connectivity")
    @Operation(summary = "检测文本模型连通性")
    @Parameter(name = "id", description = "模型ID", required = true)
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<AiModelConnectivityRespVO> testTextConnectivity(@RequestParam("id") Long id) {
        return success(aiModelService.testTextModelConnectivity(id));
    }
}

