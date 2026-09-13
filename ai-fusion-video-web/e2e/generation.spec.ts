import { expect, test } from "@playwright/test";
import { OWNER_USER, newAuthedPage } from "./helpers/auth";

/**
 * 创作工作台冒烟(对部署系统 localhost:8081):
 * - 路径1 独立生图:/generate/images(重定向到生图编辑器)→ 编辑器加载 → 填提示词 → 生成按钮可点(不点击)
 * - 路径2 参考生图:/generate/image?modelId=15 编辑器加载并选中参考图生图模型
 * - 路径5 高清处理入口:/generate/videos(重定向到万能导演台)页面加载
 */
test.describe("创作工作台:生图与生视频入口", () => {
  test.beforeEach(async ({ page, request, baseURL }) => {
    await newAuthedPage(page, request, OWNER_USER, baseURL!);
  });

  test("路径1 独立生图:图像创作编辑器填写提示词后生成按钮可点", async ({ page }) => {
    await page.goto("/generate/images");

    // 部署版本:/generate/images 服务端重定向到生图编辑器
    await page.waitForURL("**/generate/image");
    await expect(page.getByRole("heading", { name: "图像创作", level: 1 })).toBeVisible();

    // 编辑器加载:模型选择 + 提示词输入
    const modelTrigger = page.getByLabel("选择生成模型");
    await expect(modelTrigger).toBeVisible();
    const prompt = page.getByLabel("图片提示词");
    await expect(prompt).toBeVisible();

    // 填写提示词后生成按钮变为可点(断言到提交前一步,不点击)
    await prompt.fill("E2E 冒烟:黄昏海边的灯塔,动漫风格,暖色调");
    // 历史记录卡片可能也含"生成图片"字样,限定编辑器的提交按钮
    const generateButton = page
      .locator('button[aria-label="生成图片"], footer button:has-text("生成图片")')
      .first();
    await expect(generateButton).toBeVisible();
    await expect(generateButton).toBeEnabled();
  });

  test("路径2 参考生图:直达编辑器并选中参考图生图模型", async ({ page }) => {
    await page.goto("/generate/image?modelId=15");

    // 编辑器加载
    const prompt = page.getByLabel("图片提示词");
    await expect(prompt).toBeVisible();

    // modelId=15(参考图生图)已被选中
    const modelTrigger = page.getByLabel("选择生成模型");
    await expect(modelTrigger).toBeVisible();
    await expect(modelTrigger).toContainText(/参考图/);
  });

  test("路径5 高清处理入口:生视频工作台(万能导演台)加载", async ({ page }) => {
    // 部署版本:/generate/videos 服务端重定向到统一生视频工作台
    await page.goto("/generate/videos");
    await page.waitForURL("**/generate/video");

    await expect(page.getByRole("heading", { name: "万能导演台", level: 1 })).toBeVisible();
    // 创作记录区加载
    await expect(page.getByRole("heading", { name: "创作记录" })).toBeVisible();
    // 视频提示词输入区就绪
    await expect(page.getByLabel("视频提示词")).toBeVisible();
    // 提交按钮存在(空提示词时禁用,不点击)
    await expect(
      page.locator('button[aria-label="生成视频"], footer button:has-text("生成视频")').first(),
    ).toBeVisible();
  });
});
