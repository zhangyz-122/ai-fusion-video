/**
 * ComfyUI 视频模型生成能力推荐模板。
 *
 * 字段口径与后端 GenerationModelCapabilityService / ComfyUiVideoStrategy 一致：
 * 仅覆盖下列生成能力键，合并时保留 config 中的其他字段。
 */
export interface GenerationCapabilityTemplate {
  key: "wan_i2v" | "h3_video";
  label: string;
  title: string;
  config: Record<string, unknown>;
}

/** Wan I2V：以生产验证过的 wan_i2v_standard 工作流（16fps、单参考图）为基准。 */
const WAN_I2V_TEMPLATE: Record<string, unknown> = {
  supportFirstFrame: true,
  supportLastFrame: false,
  supportReferenceImages: true,
  maxReferenceImages: 1,
  referenceImageInputFormats: ["url", "data_uri"],
  supportDataUriInput: true,
  minDuration: 1,
  maxDuration: 15,
  defaultDuration: 5,
  defaultFps: 16,
};

/** H3 视频：以 MiniMax H3 T8 工作流（24fps、首帧、最多三参考图、原生音频）为基准。 */
const H3_VIDEO_TEMPLATE: Record<string, unknown> = {
  supportFirstFrame: true,
  supportLastFrame: false,
  supportReferenceImages: true,
  maxReferenceImages: 3,
  referenceImageInputFormats: ["url", "data_uri"],
  supportDataUriInput: true,
  minDuration: 1,
  maxDuration: 15,
  defaultDuration: 5,
  defaultFps: 24,
};

export const GENERATION_CAPABILITY_TEMPLATES: readonly GenerationCapabilityTemplate[] = [
  {
    key: "wan_i2v",
    label: "WAN I2V 模板",
    title: "Wan 图生视频基准：首帧 + 1 张参考图，1-15 秒，16fps，默认 5 秒",
    config: WAN_I2V_TEMPLATE,
  },
  {
    key: "h3_video",
    label: "H3 视频模板",
    title: "MiniMax H3 基准：首帧 + 最多 3 张参考图，1-15 秒，24fps，默认 5 秒",
    config: H3_VIDEO_TEMPLATE,
  },
];

/** 将模板键值合并进当前 config，覆盖模板涉及的能力字段，保留其他字段。 */
export function applyGenerationCapabilityTemplate(
  current: Record<string, unknown>,
  template: Record<string, unknown>
): Record<string, unknown> {
  return { ...current, ...template };
}
