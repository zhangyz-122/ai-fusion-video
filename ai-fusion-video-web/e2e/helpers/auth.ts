import type { APIRequestContext, Page } from "@playwright/test";

/**
 * 冒烟测试账号(与部署环境保持一致)。
 */
export interface TestUser {
  username: string;
  password: string;
}

/** 项目属主账号:拥有带分镜内容的真实项目 */
export const OWNER_USER: TestUser = { username: "zhangyz", password: "zhangyz" };
/** 普通账号:仅能看见自己的项目 */
export const GUEST_USER: TestUser = { username: "uitest", password: "Uitest#2026" };

export interface AuthSession {
  token: string;
  refreshToken: string;
  user: {
    id: number;
    username: string;
    nickname: string;
    roles: string[];
  };
}

interface ApiEnvelope<T> {
  code: number;
  msg: string;
  data: T;
}

/** 通过后端 API 登录,返回 token 与完整用户信息 */
export async function apiLogin(
  request: APIRequestContext,
  user: TestUser,
): Promise<AuthSession> {
  const loginResp = await request.post("/api/auth/login", {
    data: { username: user.username, password: user.password },
  });
  const loginBody = (await loginResp.json()) as ApiEnvelope<{
    accessToken: string;
    refreshToken: string;
  }>;
  if (!loginResp.ok() || loginBody.code !== 0) {
    throw new Error(`API 登录失败(${user.username}): ${loginBody?.msg ?? loginResp.status()}`);
  }

  const infoResp = await request.get("/api/auth/user-info", {
    headers: { Authorization: `Bearer ${loginBody.data.accessToken}` },
  });
  const infoBody = (await infoResp.json()) as ApiEnvelope<AuthSession["user"]>;
  if (!infoResp.ok() || infoBody.code !== 0) {
    throw new Error(`获取用户信息失败(${user.username}): ${infoBody?.msg ?? infoResp.status()}`);
  }

  return {
    token: loginBody.data.accessToken,
    refreshToken: loginBody.data.refreshToken,
    user: infoBody.data,
  };
}

/**
 * 将会话注入页面,等价于真实登录后的状态:
 * - auth-token cookie:proxy 中间件在"请求阶段"校验,必须在导航前写入浏览器 cookie jar;
 * - localStorage(zustand persist):前端各 store 在文档创建后读取,用 addInitScript 注入。
 */
export async function injectSession(
  page: Page,
  baseURL: string,
  session: AuthSession,
): Promise<void> {
  const host = new URL(baseURL).hostname;
  await page.context().addCookies([
    {
      name: "auth-token",
      value: session.token,
      domain: host,
      path: "/",
    },
  ]);
  await page.addInitScript(
    ({ token, refreshToken, user }) => {
      localStorage.setItem(
        "auth-storage",
        JSON.stringify({ state: { token, refreshToken, user }, version: 0 }),
      );
    },
    session,
  );
}

/** 以指定账号登录并注入页面状态(不产生真实 UI 登录) */
export async function newAuthedPage(
  page: Page,
  request: APIRequestContext,
  user: TestUser,
  baseURL: string,
): Promise<AuthSession> {
  const session = await apiLogin(request, user);
  await injectSession(page, baseURL, session);
  return session;
}
