package com.stonewu.fusion.controller.asset;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.asset.Asset;
import com.stonewu.fusion.security.SecurityUserDetails;
import com.stonewu.fusion.service.asset.AssetService;
import com.stonewu.fusion.service.project.ProjectAccessGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资产回收站控制器单元测试：三个回收站端点的归属校验与调用编排。
 * 归属校验参照 ProjectAccessGuard 模式：先解析软删行，再校验其所属项目可访问性。
 */
class AssetControllerRecycleBinTests {

    private static final Long USER_ID = 100L;

    private final AssetService assetService = mock(AssetService.class);
    private final ProjectAccessGuard accessGuard = mock(ProjectAccessGuard.class);

    private final AssetController controller = new AssetController(assetService, accessGuard);

    private Asset deletedAsset(long id, long projectId) {
        Asset asset = Asset.builder().name("回收站资产").type("character").build();
        asset.setId(id);
        asset.setProjectId(projectId);
        asset.setDeleted(true);
        return asset;
    }

    private void loginAs(Long userId) {
        SecurityUserDetails details = new SecurityUserDetails(
                userId, "zhangyz", "n/a", 1, null, List.of());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ========== 回收站列表 ==========

    @Test
    void listRecycleBinPagesDeletedAssetsOfCurrentUser() {
        loginAs(USER_ID);
        Asset deleted = deletedAsset(12L, 3L);
        when(assetService.pageDeletedInAccessibleProjects(USER_ID, 2, 50))
                .thenReturn(paged(List.of(deleted), 1));

        Map<String, Object> data = controller.listRecycleBin(2, 50).getData();

        assertThat(data.get("total")).isEqualTo(1L);
        assertThat((List<?>) data.get("records")).hasSize(1);
    }

    @Test
    void listRecycleBinRequiresLogin() {
        // 未设置 SecurityContext 时 requireCurrentUserId 抛业务异常
        assertThatThrownBy(() -> controller.listRecycleBin(1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户未登录");
        verify(assetService, never()).pageDeletedInAccessibleProjects(anyLong(), anyInt(), anyInt());
    }

    // ========== 恢复 ==========

    @Test
    void restoreChecksDeletedAssetProjectOwnershipBeforeRestoring() {
        Asset deleted = deletedAsset(12L, 3L);
        when(assetService.getDeletedById(12L)).thenReturn(deleted);
        Asset restored = deletedAsset(12L, 3L);
        restored.setDeleted(false);
        when(assetService.restore(12L)).thenReturn(restored);

        Asset result = controller.restoreRecycled(12L).getData();

        assertThat(result.getDeleted()).isFalse();
        // 归属校验解析自软删行上的项目 id
        verify(accessGuard).assertProject(3L);
        verify(assetService).restore(12L);
    }

    @Test
    void restoreRejectsAssetOutsideAccessibleProjects() {
        Asset deleted = deletedAsset(12L, 3L);
        when(assetService.getDeletedById(12L)).thenReturn(deleted);
        doThrow(new BusinessException("无权访问该项目内容"))
                .when(accessGuard).assertProject(3L);

        assertThatThrownBy(() -> controller.restoreRecycled(12L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权访问该项目内容");
        verify(assetService, never()).restore(12L);
    }

    @Test
    void restoreRejectsUnknownOrNotDeletedAssetBeforeGuard() {
        when(assetService.getDeletedById(404L))
                .thenThrow(new BusinessException("回收站中不存在该资产: 404"));

        assertThatThrownBy(() -> controller.restoreRecycled(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("回收站中不存在该资产: 404");
        verify(accessGuard, never()).assertProject(any());
    }

    // ========== 彻底删除（物理删除） ==========

    @Test
    void purgeChecksDeletedAssetProjectOwnershipBeforePurging() {
        Asset deleted = deletedAsset(12L, 3L);
        when(assetService.getDeletedById(12L)).thenReturn(deleted);

        assertThat(controller.purgeRecycled(12L).getData()).isTrue();

        verify(accessGuard).assertProject(3L);
        verify(assetService).purge(12L);
    }

    @Test
    void purgeRejectsAssetOutsideAccessibleProjects() {
        Asset deleted = deletedAsset(12L, 3L);
        when(assetService.getDeletedById(12L)).thenReturn(deleted);
        doThrow(new BusinessException("无权访问该项目内容"))
                .when(accessGuard).assertProject(3L);

        assertThatThrownBy(() -> controller.purgeRecycled(12L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权访问该项目内容");
        verify(assetService, never()).purge(12L);
    }

    // ========== 无项目归属的历史软删行（孤儿行） ==========

    @Test
    void listOrphanRecycledReturnsAssetsOfCurrentUser() {
        loginAs(USER_ID);
        Asset orphan = orphanDeletedAsset(21L);
        when(assetService.listDeletedOrphansAccessibleByUser(USER_ID)).thenReturn(List.of(orphan));

        List<Asset> data = controller.listOrphanRecycled().getData();

        assertThat(data).hasSize(1);
        assertThat(data.get(0).getId()).isEqualTo(21L);
    }

    @Test
    void listOrphanRecycledRequiresLogin() {
        assertThatThrownBy(() -> controller.listOrphanRecycled())
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户未登录");
        verify(assetService, never()).listDeletedOrphansAccessibleByUser(anyLong());
    }

    @Test
    void purgeOrphanRecycledDelegatesIdsToService() {
        loginAs(USER_ID);
        when(assetService.purgeDeletedOrphans(USER_ID, List.of(21L, 22L))).thenReturn(2);

        Integer purged = controller.purgeOrphanRecycled(List.of(21L, 22L)).getData();

        assertThat(purged).isEqualTo(2);
        // 孤儿行无项目归属，不走 assertProject，权限校验在 service 内按资产归属执行
        verify(accessGuard, never()).assertProject(any());
    }

    @Test
    void purgeOrphanRecycledRequiresLogin() {
        assertThatThrownBy(() -> controller.purgeOrphanRecycled(List.of(21L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户未登录");
        verify(assetService, never()).purgeDeletedOrphans(anyLong(), anyList());
    }

    // ========== 端点元信息（路由契约防回归） ==========

    @Test
    void recycleBinEndpointsKeepHttpMethodsAndPaths() throws Exception {
        var restore = AssetController.class.getMethod("restoreRecycled", Long.class);
        var purge = AssetController.class.getMethod("purgeRecycled", Long.class);
        var list = AssetController.class.getMethod("listRecycleBin", int.class, int.class);
        var listOrphans = AssetController.class.getMethod("listOrphanRecycled");
        var purgeOrphans = AssetController.class.getMethod("purgeOrphanRecycled", List.class);

        org.assertj.core.api.Assertions.assertThat(
                restore.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class).value())
                .containsExactly("/recycle-bin/{id}/restore");
        org.assertj.core.api.Assertions.assertThat(
                purge.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class).value())
                .containsExactly("/recycle-bin/{id}");
        org.assertj.core.api.Assertions.assertThat(
                list.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class).value())
                .containsExactly("/recycle-bin");
        org.assertj.core.api.Assertions.assertThat(
                listOrphans.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class).value())
                .containsExactly("/recycle-bin/orphans");
        org.assertj.core.api.Assertions.assertThat(
                purgeOrphans.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class).value())
                .containsExactly("/recycle-bin/orphans");
    }

    @Test
    void recycleBinLiteralPathWinsOverIdPathVariable() {
        // 与既有 /all、/list、/metadata/{assetType} 相同的字面量优先机制：
        // GET /api/asset/recycle-bin 必须命中回收站列表而非 GET /{id}（否则 Long 转换失败）
        var parser = org.springframework.web.util.pattern.PathPatternParser.defaultInstance;
        var literal = parser.parse("/api/asset/recycle-bin");
        var variable = parser.parse("/api/asset/{id}");

        org.assertj.core.api.Assertions.assertThat(literal.compareTo(variable)).isNegative();
    }

    @Test
    void orphanLiteralPathWinsOverRecycleBinIdVariable() {
        // DELETE /api/asset/recycle-bin/orphans 必须命中批量清理端点而非 /recycle-bin/{id}
        var parser = org.springframework.web.util.pattern.PathPatternParser.defaultInstance;
        var literal = parser.parse("/api/asset/recycle-bin/orphans");
        var variable = parser.parse("/api/asset/recycle-bin/{id}");

        org.assertj.core.api.Assertions.assertThat(literal.compareTo(variable)).isNegative();
    }

    private Asset orphanDeletedAsset(long id) {
        Asset asset = Asset.builder().name("历史软删资产").type("image").ownerType(1).build();
        asset.setId(id);
        asset.setProjectId(null);
        asset.setDeleted(true);
        return asset;
    }

    private com.baomidou.mybatisplus.extension.plugins.pagination.Page<Asset> paged(
            List<Asset> records, long total) {
        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Asset>(1, 20);
        page.setRecords(records);
        page.setTotal(total);
        return page;
    }
}
