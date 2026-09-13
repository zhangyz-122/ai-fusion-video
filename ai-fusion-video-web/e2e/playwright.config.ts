import { defineConfig } from "@playwright/test";
import path from "node:path";

/**
 * E2E 冒烟配置:对已部署的真实系统(默认 http://localhost:8081)执行,
 * 基准地址可通过环境变量 E2E_BASE_URL(或 BASE_URL)覆盖。
 *
 * 运行(在 ai-fusion-video-web 目录):
 *   corepack pnpm exec playwright test -c e2e/playwright.config.ts
 */
const baseURL = process.env.E2E_BASE_URL ?? process.env.BASE_URL ?? "http://localhost:8081";

export default defineConfig({
  testDir: path.resolve(__dirname),
  testMatch: /.*\.spec\.ts/,
  // 冒烟对共享的真实系统执行,串行跑避免相互干扰
  fullyParallel: false,
  workers: 1,
  retries: 1,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [["list"]],
  outputDir: path.join(__dirname, ".artifacts"),
  use: {
    baseURL,
    locale: "zh-CN",
    // 桌面宽度需覆盖 2xl 断点(1536px),保证分镜/剧本页三栏布局可见
    viewport: { width: 1728, height: 960 },
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },
});
