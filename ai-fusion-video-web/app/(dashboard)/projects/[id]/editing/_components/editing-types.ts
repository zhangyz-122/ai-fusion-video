import type {
  EditingEpisodeNode,
  EditingShotVideo,
} from "@/lib/api/editing";
import { resolveShotVideo } from "@/lib/api/editing";
import type { StoryboardEpisode, StoryboardItem, StoryboardScene } from "@/lib/api/storyboard";

// ========== 剪辑工作区 UI 类型 ==========

/** 时间线中的扁平镜头行 */
export interface EditingShotRow {
  item: StoryboardItem;
  episodeId: number;
  episodeLabel: string;
  sceneId: number;
  sceneLabel: string;
}

/** 左侧目录的过滤范围 */
export type ShotFilter =
  | { type: "all" }
  | { type: "episode"; episodeId: number }
  | { type: "scene"; episodeId: number; sceneId: number };

/** 侧栏树节点（附带镜头数量，供展示） */
export interface EditingEpisodeTree {
  episode: StoryboardEpisode;
  episodeLabel: string;
  sceneCount: number;
  shotCount: number;
  scenes: {
    scene: StoryboardScene;
    sceneLabel: string;
    shotCount: number;
    /** 场次下的镜头叶子（按默认顺序） */
    shots: {
      itemId: number;
      shotLabel: string;
      hasVideo: boolean;
    }[];
  }[];
}

/** 镜头卡片所需的解析结果 */
export interface ShotCardVideo {
  video: EditingShotVideo;
  /** 已解析为可播放的绝对地址 */
  resolvedUrl: string | null;
}

// ========== 文案工具 ==========

export function formatEpisodeLabel(episode: StoryboardEpisode): string {
  const title = episode.title?.trim();
  if (title) return title;
  return episode.episodeNumber != null
    ? `第 ${episode.episodeNumber} 集`
    : `分集 ${episode.id}`;
}

export function formatSceneLabel(scene: StoryboardScene): string {
  const heading = scene.sceneHeading?.trim();
  if (heading) return heading;
  return scene.sceneNumber
    ? `场次 ${scene.sceneNumber}`
    : `场次 ${scene.id}`;
}

/** 将时间线数据展开为扁平镜头行（默认顺序：分集 → 场次 → 镜头） */
export function flattenTimeline(
  episodes: EditingEpisodeNode[]
): EditingShotRow[] {
  return episodes.flatMap((episodeNode) =>
    episodeNode.scenes.flatMap((sceneNode) =>
      sceneNode.items.map((item) => ({
        item,
        episodeId: episodeNode.episode.id,
        episodeLabel: formatEpisodeLabel(episodeNode.episode),
        sceneId: sceneNode.scene.id,
        sceneLabel: formatSceneLabel(sceneNode.scene),
      }))
    )
  );
}

/** 将分集树转换为侧栏展示结构 */
export function buildEpisodeTree(
  episodes: EditingEpisodeNode[],
  shotVideos: Record<number, EditingShotVideo>
): EditingEpisodeTree[] {
  return episodes.map((episodeNode) => ({
    episode: episodeNode.episode,
    episodeLabel: formatEpisodeLabel(episodeNode.episode),
    sceneCount: episodeNode.scenes.length,
    shotCount: episodeNode.scenes.reduce(
      (sum, sceneNode) => sum + sceneNode.items.length,
      0
    ),
    scenes: episodeNode.scenes.map((sceneNode) => ({
      scene: sceneNode.scene,
      sceneLabel: formatSceneLabel(sceneNode.scene),
      shotCount: sceneNode.items.length,
      shots: sceneNode.items.map((item) => {
        const video = resolveShotVideo(item, shotVideos);
        return {
          itemId: item.id,
          shotLabel: item.shotNumber || item.autoShotNumber || String(item.id),
          hasVideo: !!video.videoUrl,
        };
      }),
    })),
  }));
}

export function matchesFilter(
  row: EditingShotRow,
  filter: ShotFilter
): boolean {
  switch (filter.type) {
    case "all":
      return true;
    case "episode":
      return row.episodeId === filter.episodeId;
    case "scene":
      return row.sceneId === filter.sceneId;
  }
}
