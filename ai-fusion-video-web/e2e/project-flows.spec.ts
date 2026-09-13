import { expect, test } from "@playwright/test";
import { OWNER_USER, apiLogin, newAuthedPage } from "./helpers/auth";
import { findProjectWithStoryboardItems } from "./helpers/discovery";

/**
 * 项目内创作路径冒烟(对部署系统 localhost:8081):
 * - 路径3 图生视频生产:项目概览 → 分镜页 → 打开镜头生产抽屉(不启动生产)
 * - 路径4 对白片段/自动分块:原文页"转剧本"面板完整(不启动解析)
 * - 路径6 项目成片:项目概览 → 分镜页 → 分集合成入口可见(不点击合成)
 *
 * 部署版本的项目概览是"写/定/拆/拍/剪"阶段流,分镜入口为"拆"阶段链接。
 */
test.describe("项目内创作路径", () => {
  test.beforeEach(async ({ page, request, baseURL }) => {
    await newAuthedPage(page, request, OWNER_USER, baseURL!);
  });

  async function openStoryboards(page: import("@playwright/test").Page, projectId: number) {
    await page.goto(`/projects/${projectId}`);
    // 项目概览:阶段流中的"拆"(分镜)入口
    const boardLink = page.locator('a[href$="/storyboards"]').first();
    await expect(boardLink).toBeVisible();
    await boardLink.click();
    await page.waitForURL(`**/projects/${projectId}/storyboards`);
  }

  test("路径3 图生视频生产:分镜页打开镜头生产抽屉", async ({ page, request }) => {
    const session = await apiLogin(request, OWNER_USER);
    const target = await findProjectWithStoryboardItems(request, session.token);

    await openStoryboards(page, target.id);

    // 分镜页加载:工具栏与分镜内容
    await expect(page.getByRole("button", { name: /AI 补全|AI 生成/ })).toBeVisible();
    await expect(page.locator("[data-scene-id]").first()).toBeVisible();

    // 行内"生成视频"按钮(生产抽屉触发器,无 aria-label 且侧栏"剪 · 成片"同为视频图标,限定 main 区域)
    const produceButton = page.locator("main button:has(svg.lucide-video)").first();
    await expect(produceButton).toBeAttached();
    await produceButton.click();

    // 生产抽屉打开,断言到生产按钮可见即止(不启动生产;就绪度不足时按钮可能禁用)
    await expect(page.getByText("三候选生产").first()).toBeVisible();
    const startProduction = page.getByRole("button", { name: "生产这一镜" });
    await expect(startProduction).toBeVisible();
  });

  test("路径4 对白片段/自动分块:原文页转剧本面板完整", async ({ page, request }) => {
    const session = await apiLogin(request, OWNER_USER);
    const target = await findProjectWithStoryboardItems(request, session.token);

    // 部署版本:故事转剧本入口位于"写 · 原文"页
    await page.goto(`/projects/${target.id}/source`);
    await expect(page.getByRole("heading", { name: "小说 / 故事原文", level: 1 })).toBeVisible();

    // 转剧本面板加载(原文非空时渲染)
    const panel = page.getByRole("heading", { name: "转剧本", level: 3 });
    await expect(panel).toBeVisible();

    // 创作模型选择可见(管理员账号)
    await expect(page.getByLabel(/创作模型/)).toBeVisible();

    // 按原文长度展示对应表单(短文:创作集数;长文:自动分块提示)
    const startButton = page.getByRole("button", { name: /转成剧本|开始自动分块解析/ });
    await expect(startButton).toBeVisible();
    const episodeTarget = page.getByLabel(/创作集数/);
    if (await episodeTarget.isVisible()) {
      await episodeTarget.fill("2");
    }
    await expect(startButton).toBeEnabled();
    // 断言到启动前一步,不点击转换
  });

  test("路径6 项目成片:项目概览进入分镜页可见分集合成入口", async ({ page, request }) => {
    const session = await apiLogin(request, OWNER_USER);
    const target = await findProjectWithStoryboardItems(request, session.token);

    await openStoryboards(page, target.id);

    // 分集合成入口可见(任意状态:合成 / 查看成片 / 重试 / 进行中),不点击
    const composeEntry = page
      .locator(
        'button[title="将本集所有镜头视频按顺序拼接成一个完整视频"], ' +
          'button:has-text("合成本集视频"), button:has-text("查看本集视频"), ' +
          'button:has-text("重试合成"), button:has-text("合成中")',
      )
      .first();
    await expect(composeEntry).toBeVisible();
  });
});
