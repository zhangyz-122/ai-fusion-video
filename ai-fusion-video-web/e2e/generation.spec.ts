import { expect, test } from "@playwright/test";
import { OWNER_USER, newAuthedPage } from "./helpers/auth";

/**
 * 创作工作台冒烟(集成版:工坊为目录页,经"使用此模型"进入编辑器):
 * - 路径1 独立生图:图像工坊 → 使用模型 → 编辑器 → 填提示词 → 生成按钮可点(不点击)
 * - 路径2 参考生图:/generate/image?modelId=15 编辑器加载并选中参考图生图模型
 * - 路径5 高清处理入口:视频工坊目录页加载且含高清放大能力卡片
 */
test.describe("创作工作台:生图与生视频入口", () => {
  test.beforeEach(async ({ page, request, baseURL }) => {
    await newAuthedPage(page, request, OWNER_USER, baseURL!);
  });

  test("路径1 独立生图:图像工坊选择模型并在编辑器填写提示词", async ({ page }) => {
    await page.goto("/generate/images");

    // 图像工坊目录页加载,已配置的生图能力出现
    await expect(page.getByRole("heading", { name: "图像工坊", level: 1 })).toBeVisible();
    const useModelLinks = page.getByRole("link", { name: "使用此模型" });
    await expect(useModelLinks.first()).toBeVisible();

    // 使用第一个生图模型进入编辑器
    await useModelLinks.first().click();
    await page.waitForURL(/\/generate\/image\?modelId=\d+/);

    // 编辑器加载:模型选择 + 提示词输入
    await expect(page.getByLabel("选择生成模型")).toBeVisible();
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

  test("路径5 高清处理入口:视频工坊目录加载且含高清放大能力卡片", async ({ page }) => {
    await page.goto("/generate/videos");

    // 视频工坊目录页加载
    await expect(page.getByRole("heading", { name: "视频工坊", level: 1 })).toBeVisible();
    // 高清处理(视频放大)能力以卡片形式展示(已启用卡片 + 待接入列表同名条目,取第一个)
    await expect(page.getByRole("heading", { name: /SeedVR2/ }).first()).toBeVisible();
    await expect(page.getByRole("link", { name: "使用此模型" }).first()).toBeVisible();
  });
});
