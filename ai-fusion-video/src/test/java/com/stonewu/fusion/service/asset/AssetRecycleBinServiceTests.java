package com.stonewu.fusion.service.asset;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.asset.Asset;
import com.stonewu.fusion.entity.project.Project;
import com.stonewu.fusion.mapper.asset.AssetItemMapper;
import com.stonewu.fusion.mapper.asset.AssetMapper;
import com.stonewu.fusion.service.project.ProjectService;
import com.stonewu.fusion.service.team.TeamService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资产回收站服务单元测试：软删行分页查询、恢复、物理删除。
 * 软删行被 @TableLogic 过滤，只能经 AssetMapper 自定义 SQL 触达，
 * 因此重点校验服务层调用了正确的绕过方法并维护缓存语义。
 */
class AssetRecycleBinServiceTests {

    private final AssetMapper assetMapper = mock(AssetMapper.class);
    private final AssetItemMapper assetItemMapper = mock(AssetItemMapper.class);
    private final ProjectService projectService = mock(ProjectService.class);
    private final TeamService teamService = mock(TeamService.class);

    private final AssetService assetService =
            new AssetService(assetMapper, assetItemMapper, projectService, teamService);

    private static final Long USER_ID = 100L;

    private Asset deletedAsset(long id, long projectId) {
        Asset asset = Asset.builder().name("回收站资产").type("character").build();
        asset.setId(id);
        asset.setProjectId(projectId);
        asset.setDeleted(true);
        return asset;
    }

    // ========== 回收站分页列表 ==========

    @Test
    void pageDeletedQueriesOnlyAccessibleProjectsOrderedByDeleteTime() {
        when(projectService.listAccessibleByUser(USER_ID))
                .thenReturn(List.of(project(3L), project(5L)));
        Page<Asset> page = new Page<>(1, 20);
        page.setRecords(List.of(deletedAsset(12L, 3L)));
        page.setTotal(1);
        when(assetMapper.selectDeletedPage(any(Page.class), eq(List.of(3L, 5L))))
                .thenReturn(page);

        Page<Asset> result = (Page<Asset>) assetService.pageDeletedInAccessibleProjects(USER_ID, 1, 20);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getId()).isEqualTo(12L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> idsCaptor =
                ArgumentCaptor.forClass((Class) Collection.class);
        verify(assetMapper).selectDeletedPage(any(Page.class), idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(3L, 5L);
    }

    @Test
    void pageDeletedReturnsEmptyPageWithoutHittingMapperWhenNoAccessibleProjects() {
        when(projectService.listAccessibleByUser(USER_ID)).thenReturn(List.of());

        var result = assetService.pageDeletedInAccessibleProjects(USER_ID, 1, 20);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
        verify(assetMapper, never()).selectDeletedPage(any(), anyCollection());
    }

    // ========== 软删行解析（供控制器归属校验） ==========

    @Test
    void getDeletedByIdReturnsDeletedRowViaCustomSql() {
        Asset deleted = deletedAsset(12L, 3L);
        when(assetMapper.selectDeletedById(12L)).thenReturn(deleted);

        assertThat(assetService.getDeletedById(12L)).isSameAs(deleted);
    }

    @Test
    void getDeletedByIdRejectsMissingOrStillAliveAsset() {
        when(assetMapper.selectDeletedById(404L)).thenReturn(null);

        assertThatThrownBy(() -> assetService.getDeletedById(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("回收站中不存在该资产: 404");
    }

    // ========== 恢复 ==========

    @Test
    void restoreFlipsDeletedFlagAndReturnsReloadedAsset() {
        Asset restored = Asset.builder().name("回收站资产").type("character").build();
        restored.setId(12L);
        restored.setProjectId(3L);
        restored.setDeleted(false);
        when(assetMapper.restoreById(12L)).thenReturn(1);
        when(assetMapper.selectById(12L)).thenReturn(restored);

        Asset result = assetService.restore(12L);

        assertThat(result.getId()).isEqualTo(12L);
        assertThat(result.getDeleted()).isFalse();
        verify(assetMapper).restoreById(12L);
        verify(assetMapper).selectById(12L);
    }

    @Test
    void restoreRejectsWhenNoDeletedRowAffected() {
        when(assetMapper.restoreById(404L)).thenReturn(0);

        assertThatThrownBy(() -> assetService.restore(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("回收站中不存在该资产: 404");
        verify(assetMapper, never()).selectById(404L);
    }

    // ========== 彻底删除（物理删除） ==========

    @Test
    void purgePhysicallyRemovesAssetItemsThenAssetRow() {
        assetService.purge(12L);

        var order = inOrder(assetItemMapper, assetMapper);
        order.verify(assetItemMapper).deletePhysicallyByAssetId(12L);
        order.verify(assetMapper).deletePhysicallyById(12L);
        // 既有逻辑删除入口不得被复用（deleteById 只会置 deleted = 1）
        verify(assetMapper, never()).deleteById(org.mockito.ArgumentMatchers.<java.io.Serializable>any());
    }

    private Project project(Long id) {
        Project project = new Project();
        project.setId(id);
        return project;
    }
}
