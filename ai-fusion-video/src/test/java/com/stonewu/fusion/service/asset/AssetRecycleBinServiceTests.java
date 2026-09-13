package com.stonewu.fusion.service.asset;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.asset.Asset;
import com.stonewu.fusion.entity.asset.AssetItem;
import com.stonewu.fusion.entity.project.Project;
import com.stonewu.fusion.mapper.asset.AssetItemMapper;
import com.stonewu.fusion.mapper.asset.AssetMapper;
import com.stonewu.fusion.service.project.ProjectService;
import com.stonewu.fusion.service.storage.MediaStorageService;
import com.stonewu.fusion.service.team.TeamService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资产回收站服务单元测试：软删行分页查询、恢复（含子资产）、物理删除（含媒体清理）、
 * 无项目归属历史软删行的查询与批量清理。
 * 软删行被 @TableLogic 过滤，只能经 AssetMapper 自定义 SQL 触达，
 * 因此重点校验服务层调用了正确的绕过方法并维护缓存语义。
 */
class AssetRecycleBinServiceTests {

    private final AssetMapper assetMapper = mock(AssetMapper.class);
    private final AssetItemMapper assetItemMapper = mock(AssetItemMapper.class);
    private final ProjectService projectService = mock(ProjectService.class);
    private final TeamService teamService = mock(TeamService.class);
    private final MediaStorageService mediaStorageService = mock(MediaStorageService.class);

    private final AssetService assetService =
            new AssetService(assetMapper, assetItemMapper, projectService, teamService, mediaStorageService);

    private static final Long USER_ID = 100L;

    private Asset deletedAsset(long id, long projectId) {
        Asset asset = Asset.builder().name("回收站资产").type("character").build();
        asset.setId(id);
        asset.setProjectId(projectId);
        asset.setDeleted(true);
        return asset;
    }

    private Asset orphanDeletedAsset(long id, Long userId) {
        Asset asset = Asset.builder().name("历史软删资产").type("image").ownerType(1).build();
        asset.setId(id);
        asset.setProjectId(null);
        asset.setUserId(userId);
        asset.setDeleted(true);
        return asset;
    }

    private AssetItem item(long id, long assetId, String imageUrl, String thumbnailUrl) {
        AssetItem item = AssetItem.builder().assetId(assetId).itemType("front").build();
        item.setId(id);
        item.setImageUrl(imageUrl);
        item.setThumbnailUrl(thumbnailUrl);
        return item;
    }

    private void stubOrphanTeamAccess(boolean currentTeamExists, List<Long> memberUserIds) {
        when(teamService.getCurrentTeamIdByUser(USER_ID))
                .thenReturn(currentTeamExists ? 9L : null);
        when(teamService.listMemberUserIds(9L)).thenReturn(memberUserIds);
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
    void restoreAlsoRestoresSoftDeletedAssetItems() {
        // 项目级联删除会连同子资产一起软删，恢复主资产必须同步恢复子资产
        when(assetMapper.restoreById(12L)).thenReturn(1);
        when(assetItemMapper.restoreDeletedByAssetId(12L)).thenReturn(2);
        when(assetMapper.selectById(12L))
                .thenReturn(deletedAsset(12L, 3L));

        assetService.restore(12L);

        // 子资产恢复必须在主资产恢复成功之后
        var order = inOrder(assetMapper, assetItemMapper);
        order.verify(assetMapper).restoreById(12L);
        order.verify(assetItemMapper).restoreDeletedByAssetId(12L);
    }

    @Test
    void restoreRejectsWhenNoDeletedRowAffected() {
        when(assetMapper.restoreById(404L)).thenReturn(0);

        assertThatThrownBy(() -> assetService.restore(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("回收站中不存在该资产: 404");
        verify(assetMapper, never()).selectById(404L);
        // 主资产恢复失败时不得误恢复子资产
        verify(assetItemMapper, never()).restoreDeletedByAssetId(anyLong());
    }

    // ========== 彻底删除（物理删除 + 媒体清理） ==========

    @Test
    void purgePhysicallyRemovesAssetItemsThenAssetRow() {
        when(assetMapper.selectDeletedById(12L)).thenReturn(deletedAsset(12L, 3L));
        when(assetItemMapper.selectPhysicallyByAssetId(12L)).thenReturn(List.of());

        assetService.purge(12L);

        var order = inOrder(assetItemMapper, assetMapper, mediaStorageService);
        order.verify(assetItemMapper).deletePhysicallyByAssetId(12L);
        order.verify(assetMapper).deletePhysicallyById(12L);
        // 既有逻辑删除入口不得被复用（deleteById 只会置 deleted = 1）
        verify(assetMapper, never()).deleteById(org.mockito.ArgumentMatchers.<java.io.Serializable>any());
    }

    @Test
    void purgeCollectsMediaUrlsFromCoverAndAllItemsThenCleansFiles() {
        Asset deleted = deletedAsset(12L, 3L);
        deleted.setCoverUrl("/media/covers/cover.png");
        when(assetMapper.selectDeletedById(12L)).thenReturn(deleted);
        when(assetItemMapper.selectPhysicallyByAssetId(12L)).thenReturn(List.of(
                item(1L, 12L, "/media/images/a.png", "/media/thumbs/a.png"),
                item(2L, 12L, "https://cdn.example.com/b.png", null)));

        assetService.purge(12L);

        // 封面 + 子资产图片/缩略图整体交给门面；外链与空白项由门面忽略
        verify(mediaStorageService).deleteByMediaUrls(List.of(
                "/media/covers/cover.png",
                "/media/images/a.png", "/media/thumbs/a.png",
                "https://cdn.example.com/b.png"));
    }

    @Test
    void purgeCleansMediaFilesOnlyAfterRowsArePhysicallyRemoved() {
        Asset deleted = deletedAsset(12L, 3L);
        deleted.setCoverUrl("/media/covers/cover.png");
        when(assetMapper.selectDeletedById(12L)).thenReturn(deleted);
        when(assetItemMapper.selectPhysicallyByAssetId(12L))
                .thenReturn(List.of(item(1L, 12L, "/media/images/a.png", null)));

        assetService.purge(12L);

        // 行删除先于文件清理：文件先删、事务回滚会造成恢复后媒体丢失
        var order = inOrder(assetMapper, mediaStorageService);
        order.verify(assetMapper).deletePhysicallyById(12L);
        order.verify(mediaStorageService).deleteByMediaUrls(anyList());
    }

    @Test
    void purgeRejectsMissingOrStillAliveAsset() {
        when(assetMapper.selectDeletedById(404L)).thenReturn(null);

        assertThatThrownBy(() -> assetService.purge(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("回收站中不存在该资产: 404");
        verify(assetMapper, never()).deletePhysicallyById(anyLong());
        verify(assetItemMapper, never()).deletePhysicallyByAssetId(anyLong());
        verify(mediaStorageService, never()).deleteByMediaUrls(anyList());
    }

    // ========== 无项目归属的历史软删行（孤儿行） ==========

    @Test
    void listOrphansKeepsOnlyAssetsAccessibleByOwnership() {
        Asset own = orphanDeletedAsset(21L, USER_ID);
        Asset stranger = orphanDeletedAsset(22L, 999L);
        Asset memberCreated = orphanDeletedAsset(23L, 888L);
        when(assetMapper.selectDeletedWithoutProject())
                .thenReturn(List.of(own, stranger, memberCreated));
        stubOrphanTeamAccess(true, List.of(888L));

        List<Asset> result = assetService.listDeletedOrphansAccessibleByUser(USER_ID);

        // 本人创建的可见；同团队成员创建的可见；陌生用户的不可见
        assertThat(result).extracting(Asset::getId).containsExactly(21L, 23L);
    }

    @Test
    void listOrphansRejectsRowsWithoutAnyOwnershipLink() {
        Asset stranger = orphanDeletedAsset(22L, 999L);
        when(assetMapper.selectDeletedWithoutProject()).thenReturn(List.of(stranger));
        // 无当前团队时连成员列表分支都不可达
        stubOrphanTeamAccess(false, List.of());

        List<Asset> result = assetService.listDeletedOrphansAccessibleByUser(USER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void purgeOrphansPurgesEveryAccessibleOrphanAndReturnsCount() {
        Asset first = orphanDeletedAsset(21L, USER_ID);
        Asset second = orphanDeletedAsset(22L, USER_ID);
        when(assetMapper.selectDeletedByIds(List.of(21L, 22L))).thenReturn(List.of(first, second));

        int purged = assetService.purgeDeletedOrphans(USER_ID, List.of(21L, 21L, 22L));

        assertThat(purged).isEqualTo(2);
        // 重复 id 去重后整体清理
        verify(assetMapper).deletePhysicallyById(21L);
        verify(assetMapper).deletePhysicallyById(22L);
        verify(assetItemMapper).deletePhysicallyByAssetId(21L);
        verify(assetItemMapper).deletePhysicallyByAssetId(22L);
    }

    @Test
    void purgeOrphansAbortsWhenSomeIdsAreNotProjectlessDeletedRows() {
        // 只命中 1/2 个 id：另一个不存在、有项目归属或未删除
        when(assetMapper.selectDeletedByIds(List.of(21L, 30L)))
                .thenReturn(List.of(orphanDeletedAsset(21L, USER_ID)));

        assertThatThrownBy(() -> assetService.purgeDeletedOrphans(USER_ID, List.of(21L, 30L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("部分资产不在无项目归属的历史软删行中，已取消批量清理");
        verify(assetMapper, never()).deletePhysicallyById(anyLong());
        verify(assetItemMapper, never()).deletePhysicallyByAssetId(anyLong());
    }

    @Test
    void purgeOrphansAbortsWhenAnyOrphanIsNotOwnedByCurrentUser() {
        Asset own = orphanDeletedAsset(21L, USER_ID);
        Asset teamCreated = orphanDeletedAsset(23L, 888L);
        when(assetMapper.selectDeletedByIds(List.of(21L, 23L))).thenReturn(List.of(own, teamCreated));
        // 当前用户无团队，23 号由其他团队用户创建 → 越权，整体失败
        stubOrphanTeamAccess(false, List.of());

        assertThatThrownBy(() -> assetService.purgeDeletedOrphans(USER_ID, List.of(21L, 23L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权清理资产: 23");
        verify(assetMapper, never()).deletePhysicallyById(anyLong());
    }

    @Test
    void purgeOrphansRejectsEmptyIdList() {
        assertThatThrownBy(() -> assetService.purgeDeletedOrphans(USER_ID, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请提供要清理的历史软删资产 id");
        assertThatThrownBy(() -> assetService.purgeDeletedOrphans(USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请提供要清理的历史软删资产 id");
        verify(assetMapper, never()).selectDeletedByIds(anyCollection());
    }

    private Project project(Long id) {
        Project project = new Project();
        project.setId(id);
        return project;
    }
}
