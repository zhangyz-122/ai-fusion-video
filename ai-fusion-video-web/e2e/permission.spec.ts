import { expect, test } from "@playwright/test";
import { GUEST_USER, OWNER_USER, apiLogin, injectSession } from "./helpers/auth";
import { listProjects } from "./helpers/discovery";

/**
 * 权限对照冒烟:uitest(普通账号)不能看到/访问 zhangyz 的项目。
 */
test.describe("权限对照:uitest 与 zhangyz 数据隔离", () => {
  test("uitest 项目列表不包含 zhangyz 的项目", async ({ page, request, baseURL }) => {
    const ownerSession = await apiLogin(request, OWNER_USER);
    const ownerProjects = await listProjects(request, ownerSession.token);
    expect(ownerProjects.length).toBeGreaterThan(0);

    const guestSession = await apiLogin(request, GUEST_USER);
    const guestProjects = await listProjects(request, guestSession.token);
    await injectSession(page, baseURL!, guestSession);

    await page.goto("/projects");
    await expect(page.getByRole("heading", { name: "项目管理" })).toBeVisible();

    // 列表加载完成(出现新建入口或空状态提示)
    await expect(page.getByText("新建项目")).toBeVisible();

    // zhangyz 的所有项目都不应出现在 uitest 的列表里
    for (const project of ownerProjects) {
      await expect(
        page.getByRole("heading", { name: project.name }),
      ).toHaveCount(0);
    }

    // uitest 自己的项目(若有)正常展示
    if (guestProjects.length > 0) {
      await expect(
        page.getByRole("heading", { name: guestProjects[0].name }),
      ).toBeVisible();
    }
  });

  test("uitest 通过 API 无法读取 zhangyz 的项目详情", async ({ request }) => {
    const ownerSession = await apiLogin(request, OWNER_USER);
    const ownerProjects = await listProjects(request, ownerSession.token);
    const target = ownerProjects[0];

    const guestSession = await apiLogin(request, GUEST_USER);
    const resp = await request.get(`/api/project/${target.id}`, {
      headers: { Authorization: `Bearer ${guestSession.token}` },
    });
    const body = await resp.json();
    // 要么 HTTP 非失败但业务码非 0,要么直接拒绝;不允许返回目标项目数据
    const leaked =
      resp.ok() && body.code === 0 && body.data && body.data.id === target.id;
    expect(leaked).toBeFalsy();
  });
});
