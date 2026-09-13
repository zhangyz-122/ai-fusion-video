import { API_BASE_URL, http } from "./client";

// ========== 类型定义 ==========

export interface Script {
  id: number;
  projectId: number;
  title: string;
  content: string | null;
  rawContent: string | null;
  totalEpisodes: number;
  storySynopsis: string | null;
  charactersJson: string | null;
  sourceType: number;
  parsingStatus: number;
  parsingProgress: string | null;
  summary: string | null;
  genre: string | null;
  targetAudience: string | null;
  durationEstimate: number | null;
  aiGenerated: boolean;
  status: number;
  createTime: string;
  updateTime: string;
}

export interface ScriptEpisode {
  id: number;
  scriptId: number;
  episodeNumber: number;
  title: string;
  synopsis: string | null;
  rawContent: string | null;
  durationEstimate: number | null;
  totalScenes: number;
  sourceType: number;
  sortOrder: number;
  parsingStatus: number;
  status: number;
  version: number;
  createTime: string;
  updateTime: string;
}

/** 对白/动作元素 */
export interface DialogueElement {
  type: number; // 1=对白, 2=动作, 3=旁白, 4=镜头指令, 5=环境描写
  character_name?: string;
  character_asset_id?: number;
  content: string;
  parenthetical?: string;
  sortOrder?: number;
}

export interface SceneItem {
  id: number;
  episodeId: number;
  scriptId: number;
  sceneNumber: string;
  sceneHeading: string;
  location: string | null;
  timeOfDay: string | null;
  intExt: string | null;
  characters: string[] | null;
  characterAssetIds: number[] | null;
  sceneAssetId: number | null;
  propAssetIds: number[] | null;
  sceneDescription: string | null;
  dialogues: DialogueElement[] | null;
  sortOrder: number;
  status: number;
  version: number;
  createTime: string;
  updateTime: string;
}

export interface ScriptUpdateReq {
  id: number;
  content?: string;
  rawContent?: string;
  storySynopsis?: string;
  genre?: string;
  targetAudience?: string;
  durationEstimate?: number;
}

export interface EpisodeCreateReq {
  scriptId: number;
  episodeNumber?: number;
  title?: string;
  synopsis?: string;
  rawContent?: string;
  durationEstimate?: number;
  sortOrder?: number;
}

export interface EpisodeUpdateReq {
  id: number;
  title?: string;
  synopsis?: string;
  rawContent?: string;
  durationEstimate?: number;
  sortOrder?: number;
  version?: number;
}

export interface SceneCreateReq {
  episodeId: number;
  scriptId?: number;
  sceneNumber?: string;
  sceneHeading?: string;
  location?: string;
  timeOfDay?: string;
  intExt?: string;
  sceneDescription?: string;
  sortOrder?: number;
}

export interface SceneUpdateReq {
  id: number;
  episodeId?: number;
  scriptId?: number;
  sceneNumber?: string;
  sceneHeading?: string;
  location?: string;
  timeOfDay?: string;
  intExt?: string;
  characters?: string;
  characterAssetIds?: string;
  sceneAssetId?: number;
  propAssetIds?: string;
  sceneDescription?: string;
  dialogues?: string;
  sortOrder?: number;
  version?: number;
}

// ========== API ==========

/** 自动分块解析状态 */
export interface AutoSplitStatus {
  /** 解析状态：0 未解析，1 进行中，2 完成，3 失败 */
  parsingStatus: number;
  parsingProgress: string | null;
  totalEpisodes: number;
}

export const scriptApi = {
  /** 按项目获取唯一剧本 */
  getByProject: (projectId: number) =>
    http.get<never, Script | null>(`/api/script/project/${projectId}`),

  /** 获取剧本详情 */
  get: (id: number) => http.get<never, Script>(`/api/script/${id}`),

  /** 更新剧本 */
  update: (data: ScriptUpdateReq) => http.put<never, Script>("/api/script", data),

  /** 替换原文并重置结构化分集与场次 */
  replaceSource: (id: number, rawContent: string) =>
    http.put<never, Script>(`/api/script/${id}/source`, { rawContent }),

  /** 本地模型未落库时，按原文标题兜底生成分集与场次 */
  fallbackParse: (id: number) =>
    http.post<never, Script>(`/api/script/${id}/fallback-parse`),

  /** 长文本自动分块解析（章节感知分块，逐块 AI 转剧本） */
  autoSplitNovel: (id: number, modelId?: number, chunkChars?: number) =>
    http.post<never, string>(`/api/script/${id}/auto-split`, {
      ...(modelId ? { modelId } : {}),
      ...(chunkChars ? { chunkChars } : {}),
    }),

  /** 查询自动分块解析任务状态 */
  autoSplitStatus: (id: number) =>
    http.get<never, AutoSplitStatus>(`/api/script/${id}/auto-split`),

  // ========== 分集 ==========

  /** 获取分集列表 */
  listEpisodes: (scriptId: number) =>
    http.get<never, ScriptEpisode[]>(`/api/script/${scriptId}/episodes`),

  /** 获取分集详情 */
  getEpisode: (id: number) =>
    http.get<never, ScriptEpisode>(`/api/script/episode/${id}`),

  /** 创建分集 */
  createEpisode: (data: EpisodeCreateReq) =>
    http.post<never, ScriptEpisode>("/api/script/episode", data),

  /** 更新分集 */
  updateEpisode: (data: EpisodeUpdateReq) =>
    http.put<never, ScriptEpisode>("/api/script/episode", data),

  /** 删除分集 */
  deleteEpisode: (id: number) =>
    http.delete<never, boolean>(`/api/script/episode/${id}`),

  // ========== 场次 ==========

  /** 获取场次列表（按分集） */
  listScenes: (episodeId: number) =>
    http.get<never, SceneItem[]>(`/api/script/episode/${episodeId}/scenes`),

  /** 获取场次详情 */
  getScene: (id: number) => http.get<never, SceneItem>(`/api/script/scene/${id}`),

  /** 创建场次 */
  createScene: (data: SceneCreateReq) =>
    http.post<never, SceneItem>("/api/script/scene", data),

  /** 更新场次 */
  updateScene: (data: SceneUpdateReq) =>
    http.put<never, SceneItem>("/api/script/scene", data),

  /** 删除场次 */
  deleteScene: (id: number) =>
    http.delete<never, boolean>(`/api/script/scene/${id}`),
};

// ========== 字幕导出 ==========

/**
 * 下载分集 SRT 字幕文件。
 * 该接口直接返回字幕文件流而非 CommonResult JSON，需单独用 fetch 携带鉴权头取回 blob。
 */
export async function downloadEpisodeSubtitle(
  episodeId: number,
  secondsPerLine?: number
): Promise<void> {
  const query = secondsPerLine ? `?secondsPerLine=${secondsPerLine}` : "";
  const url = `${API_BASE_URL}/api/script/episode/${episodeId}/subtitle.srt${query}`;
  const { useAuthStore } = await import("@/lib/store/auth-store");
  const token = useAuthStore.getState().token;

  const response = await fetch(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  if (!response.ok) {
    let message = `导出字幕失败（HTTP ${response.status}）`;
    try {
      const body = (await response.json()) as { msg?: string };
      if (body?.msg) message = body.msg;
    } catch {
      // 错误响应不是 JSON 时保留默认提示
    }
    throw new Error(message);
  }

  const blob = await response.blob();
  const anchor = document.createElement("a");
  anchor.href = URL.createObjectURL(blob);
  anchor.download = resolveSubtitleFilename(
    response.headers.get("content-disposition"),
    episodeId
  );
  anchor.click();
  URL.revokeObjectURL(anchor.href);
}

/** 从 Content-Disposition 解析文件名，缺失时使用“字幕-分集ID.srt” */
function resolveSubtitleFilename(
  contentDisposition: string | null,
  episodeId: number
): string {
  if (contentDisposition) {
    const utf8Match = /filename\*=UTF-8''([^;]+)/i.exec(contentDisposition);
    if (utf8Match?.[1]) return decodeURIComponent(utf8Match[1]);
    const plainMatch = /filename="?([^";]+)"?/i.exec(contentDisposition);
    if (plainMatch?.[1]) return plainMatch[1];
  }
  return `字幕-分集${episodeId}.srt`;
}
