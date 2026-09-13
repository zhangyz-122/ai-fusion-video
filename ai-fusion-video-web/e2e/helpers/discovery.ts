import type { APIRequestContext } from "@playwright/test";

/**
 * 测试数据发现:通过只读 API 找到满足冒烟前置条件的真实项目,
 * 避免在用例里硬编码项目 ID(数据会被其它任务/真实使用改变)。
 */

export interface ProjectSummary {
  id: number;
  name: string;
}

export interface StoryboardProject extends ProjectSummary {
  storyboardId: number;
  /** 第一个含镜头的场次(用于打开生产抽屉) */
  sceneId: number;
  itemCount: number;
  episodeCount: number;
}

interface ApiEnvelope<T> {
  code: number;
  msg: string;
  data: T;
}

async function getJson<T>(request: APIRequestContext, url: string, token: string): Promise<T> {
  const resp = await request.get(url, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = (await resp.json()) as ApiEnvelope<T>;
  if (!resp.ok() || body.code !== 0) {
    throw new Error(`GET ${url} 失败: ${body?.msg ?? resp.status()}`);
  }
  return body.data;
}

/** 列出属主可见的全部项目 */
export async function listProjects(
  request: APIRequestContext,
  token: string,
): Promise<ProjectSummary[]> {
  const data = await getJson<ProjectSummary[]>(request, "/api/project/list", token);
  return data ?? [];
}

interface StoryboardDto {
  id: number;
  projectId: number;
}

interface StoryboardEpisodeDto {
  id: number;
}

interface StoryboardSceneDto {
  id: number;
}

interface StoryboardItemDto {
  id: number;
}

/**
 * 找到"有分镜镜头"的项目:剧本/分镜/成片冒烟都依赖真实分镜数据。
 * 只读遍历项目 → 分镜容器 → 分集 → 场次 → 镜头。
 */
export async function findProjectWithStoryboardItems(
  request: APIRequestContext,
  token: string,
): Promise<StoryboardProject> {
  const projects = await listProjects(request, token);
  for (const project of projects) {
    try {
      const storyboard = await getJson<StoryboardDto | null>(
        request,
        `/api/storyboard/project/${project.id}`,
        token,
      );
      if (!storyboard) continue;

      const episodes = await getJson<StoryboardEpisodeDto[]>(
        request,
        `/api/storyboard/${storyboard.id}/episodes`,
        token,
      );
      if (!episodes?.length) continue;

      for (const episode of episodes) {
        const scenes = await getJson<StoryboardSceneDto[]>(
          request,
          `/api/storyboard/episode/${episode.id}/scenes`,
          token,
        );
        for (const scene of scenes ?? []) {
          const items = await getJson<StoryboardItemDto[]>(
            request,
            `/api/storyboard/scene/${scene.id}/items`,
            token,
          );
          if (items?.length) {
            return {
              ...project,
              storyboardId: storyboard.id,
              sceneId: scene.id,
              itemCount: items.length,
              episodeCount: episodes.length,
            };
          }
        }
      }
    } catch {
      // 单个项目查询失败(例如无权限)不阻塞,继续找下一个
      continue;
    }
  }
  throw new Error("没有找到含分镜镜头的项目,冒烟前置数据缺失");
}
