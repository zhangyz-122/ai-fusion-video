"use client";

import { Label } from "@/components/ui/label";
import {
  CapabilityNumberField,
  ToggleSettingCard,
  getConfigBooleanValue,
  getConfigNumberValue,
  getReferenceImageInputFormats,
  withReferenceImageInputFormat,
} from "./model-config-support";
import {
  GENERATION_CAPABILITY_TEMPLATES,
  applyGenerationCapabilityTemplate,
} from "./generation-capability-templates";

/**
 * 视频模型「生成能力」分组：首帧/尾帧、参考素材及其上限、图片传递模式、时长与帧率。
 * 全部字段读写自模型 config JSON，通过 onChange 回传完整 config，不破坏其他字段。
 */
export function GenerationCapabilityEditor({
  config,
  onChange,
  showComfyUiTemplates = false,
}: {
  config: Record<string, unknown>;
  onChange: (next: Record<string, unknown>) => void;
  showComfyUiTemplates?: boolean;
}) {
  const supportsFirstFrame = getConfigBooleanValue(config.supportFirstFrame);
  const supportsLastFrame = getConfigBooleanValue(config.supportLastFrame);
  const supportsReferenceImages = getConfigBooleanValue(config.supportReferenceImages);
  const supportsReferenceVideos = getConfigBooleanValue(config.supportReferenceVideos);
  const supportsReferenceAudios = getConfigBooleanValue(config.supportReferenceAudios);
  const inputFormats = getReferenceImageInputFormats(config);
  const supportsUrlInput = inputFormats.includes("url");
  const supportsDataUriInput = inputFormats.includes("data_uri");
  const supportsAnyImageInput = supportsFirstFrame || supportsLastFrame || supportsReferenceImages;

  const updateToggle = (key: string, value: boolean) => {
    onChange({ ...config, [key]: value });
  };

  const toggleWithDependentLimit = (key: string, limitKey: string, enabled: boolean) => {
    onChange({ ...config, [key]: enabled, [limitKey]: enabled ? config[limitKey] : 0 });
  };

  const updateNumberField = (key: string, raw: string) => {
    const next = { ...config };
    if (raw.trim() === "") {
      delete next[key];
    } else {
      const parsed = Number(raw);
      next[key] = Number.isNaN(parsed) ? raw : parsed;
    }
    onChange(next);
  };

  return (
    <div className="rounded-lg border border-border/40 bg-muted/20 p-3 space-y-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <Label className="text-[11px] text-muted-foreground">生成能力</Label>
          <p className="mt-1 text-[10px] text-muted-foreground/70">
            控制 generate_video 的首帧、尾帧、参考素材、时长与帧率。
          </p>
        </div>
        {showComfyUiTemplates && (
          <div className="flex shrink-0 flex-wrap items-center gap-2" role="group" aria-label="ComfyUI 推荐配置模板">
            <span className="text-[10px] text-muted-foreground/70">推荐配置模板</span>
            {GENERATION_CAPABILITY_TEMPLATES.map(template => (
              <button
                key={template.key}
                type="button"
                onClick={() => onChange(applyGenerationCapabilityTemplate(config, template.config))}
                title={template.title}
                className="rounded-lg border border-border/40 bg-background/70 px-2 py-1 text-[10px] text-muted-foreground transition-colors hover:border-primary/40 hover:text-primary"
              >
                {template.label}
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="grid gap-2.5 [grid-template-columns:repeat(auto-fit,minmax(240px,1fr))]">
        <ToggleSettingCard
          checked={supportsFirstFrame}
          title="支持首帧图"
          description="允许传 firstFrameImageUrl 来锁定开场画面。"
          onToggle={() => updateToggle("supportFirstFrame", !supportsFirstFrame)}
        />
        <ToggleSettingCard
          checked={supportsLastFrame}
          title="支持尾帧图"
          description="允许传 lastFrameImageUrl 来约束结尾画面。"
          onToggle={() => updateToggle("supportLastFrame", !supportsLastFrame)}
        />
        <ToggleSettingCard
          checked={supportsReferenceImages}
          title="支持参考图"
          description="允许传 referenceImageUrls；适合角色、场景或多图参考。"
          onToggle={() => toggleWithDependentLimit("supportReferenceImages", "maxReferenceImages", !supportsReferenceImages)}
        />
        <ToggleSettingCard
          checked={supportsReferenceVideos}
          title="支持参考视频"
          description="允许传 referenceVideoUrls，用于动作或镜头风格参考。"
          onToggle={() => toggleWithDependentLimit("supportReferenceVideos", "maxReferenceVideos", !supportsReferenceVideos)}
        />
        <ToggleSettingCard
          checked={supportsReferenceAudios}
          title="支持参考音频"
          description="允许传 referenceAudioUrls，用于节奏或音频条件参考。"
          onToggle={() => toggleWithDependentLimit("supportReferenceAudios", "maxReferenceAudios", !supportsReferenceAudios)}
        />
      </div>

      <div className="space-y-2">
        <div>
          <Label className="text-[11px] text-muted-foreground">图片传递模式</Label>
          <p className="mt-1 text-[10px] text-muted-foreground/70">
            同时作用于首帧图、尾帧图和 referenceImageUrls；至少启用一种才能提交图片输入。
          </p>
        </div>
        <div className="grid gap-2.5 sm:grid-cols-2">
          <ToggleSettingCard
            checked={supportsUrlInput}
            title="允许 URL 传递"
            description="有公网对象存储或后端资源公网地址时，直接传递图片 URL。"
            disabled={!supportsAnyImageInput}
            onToggle={() => onChange(withReferenceImageInputFormat(config, "url", !supportsUrlInput))}
          />
          <ToggleSettingCard
            checked={supportsDataUriInput}
            title="允许 base64 / Data URI"
            description="没有公网访问地址时，将图片转换为 Data URI 后再提交。"
            disabled={!supportsAnyImageInput}
            onToggle={() => onChange(withReferenceImageInputFormat(config, "data_uri", !supportsDataUriInput))}
          />
        </div>
      </div>

      <div className="grid gap-3 [grid-template-columns:repeat(auto-fit,minmax(220px,1fr))]">
        <CapabilityNumberField
          label="最少图片输入数"
          value={getConfigNumberValue(config.minImageInputs)}
          onChange={value => updateNumberField("minImageInputs", value)}
          min={0}
          step={1}
          placeholder="例如：1"
          hint="计数包含 firstFrameImageUrl、lastFrameImageUrl 和 referenceImageUrls。"
        />
        <CapabilityNumberField
          label="最多图片输入数"
          value={getConfigNumberValue(config.maxImageInputs)}
          onChange={value => updateNumberField("maxImageInputs", value)}
          min={0}
          step={1}
          placeholder="例如：3"
          hint="用于限制图片类输入总数，避免首尾帧与参考图一起超限。"
        />
        <CapabilityNumberField
          label="最多参考图数量"
          value={getConfigNumberValue(config.maxReferenceImages)}
          onChange={value => updateNumberField("maxReferenceImages", value)}
          min={0}
          step={1}
          disabled={!supportsReferenceImages}
          placeholder="例如：3"
          hint="referenceImageUrls 的单独上限。"
        />
        <CapabilityNumberField
          label="最多参考视频数量"
          value={getConfigNumberValue(config.maxReferenceVideos)}
          onChange={value => updateNumberField("maxReferenceVideos", value)}
          min={0}
          step={1}
          disabled={!supportsReferenceVideos}
          placeholder="例如：1"
          hint="referenceVideoUrls 的单独上限。"
        />
        <CapabilityNumberField
          label="最多参考音频数量"
          value={getConfigNumberValue(config.maxReferenceAudios)}
          onChange={value => updateNumberField("maxReferenceAudios", value)}
          min={0}
          step={1}
          disabled={!supportsReferenceAudios}
          placeholder="例如：1"
          hint="referenceAudioUrls 的单独上限。"
        />
      </div>

      <div className="space-y-2 border-t border-border/20 pt-2">
        <div>
          <Label className="text-[11px] text-muted-foreground">时长与帧率</Label>
          <p className="mt-1 text-[10px] text-muted-foreground/70">
            约束 generate_video 请求的时长范围；ComfyUI 工作流按帧率换算总帧数。
          </p>
        </div>
        <div className="grid gap-3 [grid-template-columns:repeat(auto-fit,minmax(220px,1fr))]">
          <CapabilityNumberField
            label="最短时长（秒）"
            value={getConfigNumberValue(config.minDuration)}
            onChange={value => updateNumberField("minDuration", value)}
            min={1}
            max={60}
            step={1}
            placeholder="例如：4"
          />
          <CapabilityNumberField
            label="最长时长（秒）"
            value={getConfigNumberValue(config.maxDuration)}
            onChange={value => updateNumberField("maxDuration", value)}
            min={1}
            max={60}
            step={1}
            placeholder="例如：15"
          />
          <CapabilityNumberField
            label="默认时长（秒）"
            value={getConfigNumberValue(config.defaultDuration)}
            onChange={value => updateNumberField("defaultDuration", value)}
            min={1}
            max={60}
            step={1}
            placeholder="例如：5"
          />
          <CapabilityNumberField
            label="帧率（fps）"
            value={getConfigNumberValue(config.defaultFps)}
            onChange={value => updateNumberField("defaultFps", value)}
            min={1}
            max={120}
            step={1}
            placeholder="例如：16"
            hint="ComfyUI 渲染使用的默认帧率，未填写时由工作流决定。"
          />
        </div>
      </div>
    </div>
  );
}
