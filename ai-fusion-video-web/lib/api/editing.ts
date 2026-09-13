import {
  storyboardApi,
  type Storyboard,
  type StoryboardEpisode,
  type StoryboardItem,
  type StoryboardScene,
} from "./storyboard";
import { productionApi, type ProductionRun } from "./production";

// ========== 类型定义 ==========

/** 镜头可播放视频的来源 */
export type EditingShotVideoSource = "selectedTake" | "generated";

/** 镜头可播放视频（剪辑工作区使用） */
export interface EditingShotVideo {
  /** 播放地址，null 表示该镜头暂无可用视频 */
  videoUrl: string | null;
  /** 封面地址，可能为 null */
  coverUrl: string | null;
  /** selectedTake = 生产流程选中的候选视频；generated = 镜头生成视频兜底 */
  source: EditingShotVideoSource;
}

/** 分镜场次及其镜头（按 sortOrder 排序） */
export interface EditingSceneNode {
  scene: StoryboardScene;
  items: StoryboardItem[];
}

/** 分镜分集及其场次（按 sortOrder 排序） */
export interface EditingEpisodeNode {
  episode: StoryboardEpisode;
  scenes: EditingSceneNode[];
}

/** 剪辑工作区时间线数据 */
export interface EditingTimeline {
  storyboard: Storyboard | null;
  episodes: EditingEpisodeNode[];
  /** itemId → 选中 Take 的视频信息 */
  shotVideos: Record<number, EditingShotVideo>;
}

// ========== 内部工具 ==========

const SELECTED_RUNS_PAGE_SIZE = 50;
const SELECTED_RUNS_MAX_PAGES = 10;

function bySortOrder<T extends { sortOrder: number; id: number }>(a: T, b: T) {
  return a.sortOrder - b.sortOrder || a.id - b.id;
}

/** 分页拉取当前用户已选中候选视频的全部生产运行 */
async function listAllSelectedRuns(): Promise<ProductionRun[]> {
  const runs: ProductionRun[] = [];
  for (let pageNo = 1; pageNo <= SELECTED_RUNS_MAX_PAGES; pageNo += 1) {
    const page = await productionApi.list({
      status: "SELECTED",
      pageNo,
      pageSize: SELECTED_RUNS_PAGE_SIZE,
    });
    runs.push(...page.list);
    if (page.list.length < SELECTED_RUNS_PAGE_SIZE) break;
  }
  return runs;
}

/**
 * 为带选中 Take 的镜头解析候选视频地址。
 * 通过 SELECTED 运行列表定位运行，再从运行详情中取出选中候选的视频。
 */
async function resolveSelectedTakeVideos(
  items: StoryboardItem[]
): Promise<Record<number, EditingShotVideo>> {
  const selectedItems = items.filter((item) => item.selectedTakeId != null);
  if (selectedItems.length === 0) return {};

  const runs = await listAllSelectedRuns();
  const runByItemId = new Map<number, ProductionRun>();
  for (const run of runs) {
    if (runByItemId.has(run.storyboardItemId)) continue;
    const matched = selectedItems.some(
      (item) =>
        item.id === run.storyboardItemId &&
        item.selectedTakeId === run.selectedTakeId
    );
    if (matched) runByItemId.set(run.storyboardItemId, run);
  }
  if (runByItemId.size === 0) return {};

  const entries = await Promise.all(
    [...runByItemId.entries()].map(async ([itemId, run]) => {
      try {
        const detail = await productionApi.detail(run.id);
        const item = selectedItems.find((candidate) => candidate.id === itemId);
        const take = item
          ? detail.takes.find((candidate) => candidate.id === item.selectedTakeId)
          : null;
        if (!take?.videoUrl) return null;
        const video: EditingShotVideo = {
          videoUrl: take.videoUrl,
          coverUrl: take.coverUrl,
          source: "selectedTake",
        };
        return [itemId, video] as const;
      } catch (err) {
        console.error(`解析镜头 ${itemId} 的选中 Take 视频失败:`, err);
        return null;
      }
    })
  );

  return Object.fromEntries(
    entries.filter((entry): entry is readonly [number, EditingShotVideo] => entry !== null)
  );
}

// ========== API ==========

/**
 * 解析单个镜头的可播放视频：
 * 优先使用生产流程选中的候选视频，否则回退到镜头生成视频。
 */
export function resolveShotVideo(
  item: StoryboardItem,
  shotVideos: Record<number, EditingShotVideo>
): EditingShotVideo {
  const takeVideo =
    item.selectedTakeId != null ? shotVideos[item.id] : undefined;
  if (takeVideo?.videoUrl) return takeVideo;
  const fallbackUrl = item.generatedVideoUrl ?? item.videoUrl;
  return {
    videoUrl: fallbackUrl,
    coverUrl: takeVideo?.coverUrl ?? null,
    source: takeVideo ? "selectedTake" : "generated",
  };
}

export const editingApi = {
  /**
   * 加载项目剪辑时间线：分镜 → 分集 → 场次 → 镜头（均按 sortOrder 排序），
   * 并解析各镜头选中 Take 的视频。
   */
  loadTimeline: async (projectId: number): Promise<EditingTimeline> => {
    const storyboard = await storyboardApi.getByProject(projectId);
    if (!storyboard) {
      return { storyboard: null, episodes: [], shotVideos: {} };
    }

    const episodes = (await storyboardApi.listEpisodes(storyboard.id)).sort(
      bySortOrder
    );
    const episodeNodes = await Promise.all(
      episodes.map(async (episode) => {
        const scenes = (
          await storyboardApi.listScenesByEpisode(episode.id)
        ).sort(bySortOrder);
        const sceneNodes = await Promise.all(
          scenes.map(async (scene) => ({
            scene,
            items: (await storyboardApi.listItemsByScene(scene.id)).sort(
              bySortOrder
            ),
          }))
        );
        return { episode, scenes: sceneNodes };
      })
    );

    const allItems = episodeNodes.flatMap((node) =>
      node.scenes.flatMap((sceneNode) => sceneNode.items)
    );
    const shotVideos = await resolveSelectedTakeVideos(allItems);

    return { storyboard, episodes: episodeNodes, shotVideos };
  },
};
