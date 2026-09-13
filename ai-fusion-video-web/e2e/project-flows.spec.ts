import { expect, test, type Page } from "@playwright/test";
import { OWNER_USER, apiLogin, newAuthedPage } from "./helpers/auth";
import { findProjectWithStoryboardItems } from "./helpers/discovery";

/**
 * 项目内创作路径冒烟(集成版):
 * - 路径3 图生视频生产:项目概览 → 分镜页 → 选中分集 → 生产抽屉打开(不启动生产)
 * - 路径4 对白片段/自动分块:剧本页"故事转剧本"弹窗完整(不启动解析)
 * - 路径6 项目成片:项目概览 → 分镜页 → 选中分集 → 分集合成入口可见(不点击合成)
 */
test.describe("项目内创作路径", () => {
  test.beforeEach(async ({ page, request, baseURL }) => {
    await newAuthedPage(page, request, OWNER_USER, baseURL!);
  });

  async function openStoryboards(page: Page, projectId: number) {
    await page.goto(`/projects/${projectId}`);
    // 项目概览:阶段流中的"拆"(分镜)入口
    const boardLink = page.locator('a[href$="/storyboards"]').first();
    await expect(boardLink).toBeVisible();
    await boardLink.click();
    await page.waitForURL(`**/projects/${projectId}/storyboards`);
    // 分镜页加载:工具栏与分镜目录
    await expect(page.getByRole("button", { name: /AI 补全|AI 生成/ })).toBeVisible();
    await expect(page.getByRole("button", { name: "添加分镜集" }).or(page.locator("[data-scene-id]").first())).toBeVisible();
  }

  /** 选中分集树第一行:生产抽屉与合成入口都依赖已选中分集 */
  async function selectFirstEpisode(page: Page) {
    const firstEpisode = page.getByRole("button", { name: /第.{1,8}集/ }).first();
    if (await firstEpisode.isVisible().catch(() => false)) {
      await firstEpisode.click();
    }
  }

  test("路径3 图生视频生产:分镜页打开镜头生产抽屉", async ({ page, request }) => {
    const session = await apiLogin(request, OWNER_USER);
    const target = await findProjectWithStoryboardItems(request, session.token);

    await openStoryboards(page, target.id);
    await selectFirstEpisode(page);

    // 行内生产按钮(集成版带可访问名"生产镜头 N 的 3 个候选视频")
    const produceButton = page
      .getByRole("button", { name: /生产镜头 .{1,12} 的 3 个候选视频/ })
      .first();
    await expect(produceButton).toBeVisible();
    await produceButton.click();

    // 生产抽屉打开,断言到生产按钮可见即止(不启动生产;
    // 就绪度不足时按钮禁用属正常状态)
    const drawer = page.getByRole("dialog");
    await expect(drawer).toBeVisible();
    await expect(drawer.getByRole("heading", { name: /生产这一镜/ })).toBeVisible();
    await expect(drawer.getByText(/3 个候选视频/).first()).toBeVisible();
    const startProduction = drawer.getByRole("button", { name: "生产这一镜" });
    await expect(startProduction).toBeVisible();
  });

  test("路径4 对白片段/自动分块:剧本页故事转剧本弹窗完整", async ({ page, request }) => {
    const session = await apiLogin(request, OWNER_USER);
    const target = await findProjectWithStoryboardItems(request, session.token);

    await page.goto(`/projects/${target.id}/scripts`);

    // 故事转剧本入口可见并打开弹窗(不点击"开始解析")
    const storyToScript = page.getByRole("button", { name: "故事转剧本" });
    await expect(storyToScript).toBeVisible();
    await storyToScript.click();

    const dialog = page.getByRole("dialog");
    await expect(dialog.getByRole("heading", { name: "故事转剧本" })).toBeVisible();
    await expect(dialog.getByLabel("文本模型")).toBeVisible();
    // 填写分块参数(仅本地表单,不提交)
    const chunkChars = dialog.getByLabel(/每块字符数/);
    await expect(chunkChars).toBeVisible();
    await chunkChars.fill("4000");
    await expect(dialog.getByRole("button", { name: "开始解析" })).toBeEnabled();

    // 关闭弹窗,不启动解析
    await dialog.getByRole("button", { name: "取消" }).click();
    await expect(page.getByRole("dialog")).toBeHidden();
  });

  test("路径6 项目成片:项目概览进入分镜页可见分集合成入口", async ({ page, request }) => {
    const session = await apiLogin(request, OWNER_USER);
    const target = await findProjectWithStoryboardItems(request, session.token);

    await openStoryboards(page, target.id);
    // 选中分集后合成入口才渲染
    await selectFirstEpisode(page);

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
