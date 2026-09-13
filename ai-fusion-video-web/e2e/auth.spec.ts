import { expect, test } from "@playwright/test";
import { OWNER_USER } from "./helpers/auth";

/**
 * 登录 / 登出冒烟:走真实 UI 表单,不注入任何状态。
 */
test.describe("认证:登录与登出", () => {
  test("错误密码登录提示错误且不进入系统", async ({ page }) => {
    await page.goto("/login");
    await expect(page.getByPlaceholder("用户名")).toBeVisible();

    await page.getByPlaceholder("用户名").fill(OWNER_USER.username);
    await page.getByPlaceholder("密码").fill("definitely-wrong-password");
    await page.getByRole("button", { name: "登录", exact: true }).click();

    // 后端返回"用户名或密码错误",停留在登录页
    await expect(page.getByText("用户名或密码错误")).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test("登录成功进入仪表盘并显示当前用户", async ({ page }) => {
    await page.goto("/login");
    await page.getByPlaceholder("用户名").fill(OWNER_USER.username);
    await page.getByPlaceholder("密码").fill(OWNER_USER.password);
    await page.getByRole("button", { name: "登录", exact: true }).click();

    await page.waitForURL("**/dashboard", { timeout: 20_000 });
    // 仪表盘骨架渲染
    await expect(page.getByRole("heading", { level: 1 }).first()).toBeVisible({ timeout: 15_000 });
    // 顶栏头像区显示当前用户昵称
    await expect(page.locator("header").getByText(OWNER_USER.username)).toBeVisible();
  });

  test("退出登录返回登录页", async ({ page }) => {
    // 每个用例独立浏览器上下文,先通过真实 UI 登录
    await page.goto("/login");
    await page.getByPlaceholder("用户名").fill(OWNER_USER.username);
    await page.getByPlaceholder("密码").fill(OWNER_USER.password);
    await page.getByRole("button", { name: "登录", exact: true }).click();
    await page.waitForURL("**/dashboard", { timeout: 20_000 });

    // 打开头像下拉并退出
    const avatarTrigger = page.locator("header button[aria-expanded]").first();
    await expect(avatarTrigger).toBeVisible();
    await avatarTrigger.click();
    await page.getByText("退出登录").click();

    await page.waitForURL("**/login", { timeout: 20_000 });
    await expect(page.getByPlaceholder("用户名")).toBeVisible();
  });
});
