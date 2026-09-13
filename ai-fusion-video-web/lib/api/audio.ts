import { http } from "./client";
import type { ComfyUiWorkflow } from "./comfyui-workflow";

/** 声音工坊音频驱动口播能力对应的 INFINITETALK 工作流 ID。 */
export const AUDIO_WORKFLOW_ID = 11;

export const audioWorkshopApi = {
  /**
   * 读取音频能力工作流的接入状态。
   * 接口要求管理员权限;普通用户或服务异常时请求失败,
   * 由调用方降级为"能力接入中"占位,不直接报错。
   */
  getCapabilityWorkflow: () =>
    http.get<never, ComfyUiWorkflow>(
      `/api/ai/comfyui/workflow/get?id=${AUDIO_WORKFLOW_ID}`,
    ),
};
