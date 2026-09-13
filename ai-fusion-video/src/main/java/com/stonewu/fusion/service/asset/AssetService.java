package com.stonewu.fusion.service.asset;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.asset.Asset;
import com.stonewu.fusion.entity.asset.AssetItem;
import com.stonewu.fusion.entity.project.Project;
import com.stonewu.fusion.mapper.asset.AssetItemMapper;
import com.stonewu.fusion.mapper.asset.AssetMapper;
import com.stonewu.fusion.security.SecurityUtils;
import com.stonewu.fusion.service.project.ProjectService;
import com.stonewu.fusion.service.storage.MediaStorageService;
import com.stonewu.fusion.service.team.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 资产服务
 */
@Service
@RequiredArgsConstructor
public class AssetService {

    private static final int OWNER_TYPE_TEAM = 2;

    private final AssetMapper assetMapper;
    private final AssetItemMapper assetItemMapper;
    private final ProjectService projectService;
    private final TeamService teamService;
    private final MediaStorageService mediaStorageService;

    // ========== 资产 ==========

    @Cacheable(value = "asset", key = "#id")
    public Asset getById(Long id) {
        Asset asset = assetMapper.selectById(id);
        if (asset == null)
            throw new BusinessException("资产不存在: " + id);
        return asset;
    }

    public List<Asset> listByProject(Long projectId) {
        return assetMapper.selectList(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getProjectId, projectId)
                .orderByDesc(Asset::getCreateTime));
    }

    public List<Asset> listByProject(Long projectId, String type, String keyword) {
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<Asset>()
                .eq(Asset::getProjectId, projectId)
                .orderByDesc(Asset::getCreateTime);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Asset::getType, type);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Asset::getName, keyword.trim());
        }
        return assetMapper.selectList(wrapper);
    }

    public List<Map<String, Object>> listWithItemsByProject(Long projectId) {
        List<Asset> assets = listByProject(projectId);
        if (assets.isEmpty())
            return List.of();

        List<Long> assetIds = assets.stream().map(Asset::getId).collect(Collectors.toList());
        // properties is a potentially large JSON/TEXT column. Letting MySQL
        // sort the full rows by sort_order can exhaust its sort buffer. The
        // result set is already scoped to this project, so sort small groups
        // after retrieval instead.
        List<AssetItem> allItems = assetItemMapper.selectList(new LambdaQueryWrapper<AssetItem>()
                .in(AssetItem::getAssetId, assetIds));

        Map<Long, List<AssetItem>> itemsMap = allItems.stream()
                .collect(Collectors.groupingBy(AssetItem::getAssetId));
        itemsMap.values().forEach(items -> items.sort(
                Comparator.comparing(AssetItem::getSortOrder,
                        Comparator.nullsFirst(Comparator.naturalOrder()))));

        return assets.stream().map(asset -> {
            Map<String, Object> map = BeanUtil.beanToMap(asset, false, true);
            map.put("items", itemsMap.getOrDefault(asset.getId(), List.of()));
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 按用户分页查询资产（跨项目），支持可选的 projectId / type / keyword 过滤
     */
    public IPage<Asset> pageByUser(Long userId, Long projectId, String type, String keyword, int page, int size) {
        LambdaQueryWrapper<Asset> wrapper = buildUserQueryWrapper(userId, projectId, type, keyword);
        return assetMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public IPage<Asset> pageAccessibleByUser(Long userId, Long projectId, String type, String keyword, int page, int size) {
        LambdaQueryWrapper<Asset> wrapper = buildAccessibleQueryWrapper(userId, projectId, type, keyword);
        return assetMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 统计当前用户各类型资产数量（按 projectId / keyword 过滤，不按 type 过滤）
     * 返回 Map: type -> count
     */
    public Map<String, Long> countByUserGroupByType(Long userId, Long projectId, String keyword) {
        LambdaQueryWrapper<Asset> wrapper = buildUserQueryWrapper(userId, projectId, null, keyword);
        List<Asset> all = assetMapper.selectList(
                wrapper.select(Asset::getType));
        return all.stream().collect(
                Collectors.groupingBy(Asset::getType, Collectors.counting()));
    }

    public Map<String, Long> countAccessibleByUserGroupByType(Long userId, Long projectId, String keyword) {
        LambdaQueryWrapper<Asset> wrapper = buildAccessibleQueryWrapper(userId, projectId, null, keyword);
        List<Asset> all = assetMapper.selectList(
                wrapper.select(Asset::getType));
        return all.stream().collect(
                Collectors.groupingBy(Asset::getType, Collectors.counting()));
    }

    public boolean canAccessAsset(Long assetId, Long userId) {
        return canAccessAsset(getById(assetId), userId);
    }

    public boolean canAccessAsset(Asset asset, Long userId) {
        if (asset == null) {
            return false;
        }
        if (userId.equals(asset.getUserId()) || userId.equals(asset.getOwnerId())) {
            return true;
        }
        if (asset.getProjectId() != null) {
            return projectService.canAccessProject(asset.getProjectId(), userId);
        }
        Long currentTeamId = teamService.getCurrentTeamIdByUser(userId);
        if (currentTeamId == null) {
            return false;
        }
        // owner_type 历史脏数据可为 NULL（库列可空），用对象比较避免拆箱 NPE；
        // NULL 视为非团队拥有，继续走创建者是否同团队成员的判断
        if (Integer.valueOf(OWNER_TYPE_TEAM).equals(asset.getOwnerType()) && currentTeamId.equals(asset.getOwnerId())) {
            return true;
        }
        return teamService.listMemberUserIds(currentTeamId).contains(asset.getUserId());
    }

    private LambdaQueryWrapper<Asset> buildUserQueryWrapper(Long userId, Long projectId, String type, String keyword) {
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<Asset>()
                .eq(Asset::getUserId, userId)
                .orderByDesc(Asset::getUpdateTime);
        if (projectId != null) {
            wrapper.eq(Asset::getProjectId, projectId);
        }
        if (StrUtil.isNotBlank(type)) {
            wrapper.eq(Asset::getType, type);
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(Asset::getName, keyword.trim());
        }
        return wrapper;
    }

    private LambdaQueryWrapper<Asset> buildAccessibleQueryWrapper(Long userId, Long projectId, String type, String keyword) {
        Long currentTeamId = teamService.getCurrentTeamIdByUser(userId);
        if (currentTeamId == null) {
            return buildUserQueryWrapper(userId, projectId, type, keyword);
        }
        List<Long> memberUserIds = teamService.listMemberUserIds(currentTeamId);
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<Asset>()
                .and(scope -> scope
                        .and(teamOwned -> teamOwned
                                .eq(Asset::getOwnerType, OWNER_TYPE_TEAM)
                    .eq(Asset::getOwnerId, currentTeamId))
                        .or(memberOwned -> memberOwned
                                .in(Asset::getUserId, memberUserIds)))
                .orderByDesc(Asset::getUpdateTime);
        if (projectId != null) {
            wrapper.eq(Asset::getProjectId, projectId);
        }
        if (StrUtil.isNotBlank(type)) {
            wrapper.eq(Asset::getType, type);
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(Asset::getName, keyword.trim());
        }
        return wrapper;
    }

    public List<Asset> listByOwner(Integer ownerType, Long ownerId, String type) {
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<Asset>()
                .eq(Asset::getOwnerType, ownerType)
                .eq(Asset::getOwnerId, ownerId)
                .orderByDesc(Asset::getCreateTime);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Asset::getType, type);
        }
        return assetMapper.selectList(wrapper);
    }

    public List<Asset> listAccessibleByUser(Long userId, String type) {
        return assetMapper.selectList(buildAccessibleQueryWrapper(userId, null, type, null));
    }

    public Asset findByProjectTypeAndName(Long projectId, String type, String name) {
        return assetMapper.selectOne(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getProjectId, projectId)
                .eq(Asset::getType, type)
                .eq(Asset::getName, name)
                .last("LIMIT 1"));
    }

    @CacheEvict(value = { "asset", "assetItem" }, allEntries = true)
    @Transactional
    public Asset create(Asset asset) {
        validateAssetMediaUrls(asset);
        applyCurrentTeamOwnership(asset);
        assetMapper.insert(asset);

        // 自动创建初始子资产，名称使用主资产名称
        AssetItem initialItem = AssetItem.builder()
                .assetId(asset.getId())
                .itemType("initial")
                .name(asset.getName())
                .sortOrder(0)
                .sourceType(asset.getSourceType() != null ? asset.getSourceType() : 1)
                .build();
        assetItemMapper.insert(initialItem);

        return asset;
    }

    @CacheEvict(value = "asset", allEntries = true)
    @Transactional
    public Asset update(Asset asset) {
        getById(asset.getId());
        validateAssetMediaUrls(asset);
        assetMapper.updateById(asset);
        return asset;
    }

    @CacheEvict(value = "asset", allEntries = true)
    @Transactional
    public void delete(Long id) {
        assetMapper.deleteById(id);
    }

    // ========== 回收站（已逻辑删除资产） ==========

    /**
     * 分页查询当前用户可访问项目内已逻辑删除的资产（回收站），按删除时间（update_time）倒序。
     * 软删行被 @TableLogic 过滤、既有查询不可达，只能走 AssetMapper 的显式自定义 SQL。
     */
    public IPage<Asset> pageDeletedInAccessibleProjects(Long userId, int page, int size) {
        List<Long> accessibleProjectIds = projectService.listAccessibleByUser(userId).stream()
                .map(Project::getId)
                .collect(Collectors.toList());
        if (accessibleProjectIds.isEmpty()) {
            return new Page<>(page, size);
        }
        return assetMapper.selectDeletedPage(new Page<>(page, size), accessibleProjectIds);
    }

    /**
     * 查询已逻辑删除的资产，不存在或未删除时抛业务异常。
     * 供控制器在归属校验前解析软删行（selectById 自动追加 deleted = 0，无法命中）。
     */
    public Asset getDeletedById(Long id) {
        Asset asset = assetMapper.selectDeletedById(id);
        if (asset == null) {
            throw new BusinessException("回收站中不存在该资产: " + id);
        }
        return asset;
    }

    /**
     * 查询当前用户有权访问的无项目归属（project_id IS NULL）历史软删资产。
     * <p>
     * 此类行无法解析所属项目，归属校验退回资产自身归属：创建者/拥有者本人，
     * 或当前团队拥有/成员创建（复用 {@link #canAccessAsset} 的无项目分支）。
     */
    public List<Asset> listDeletedOrphansAccessibleByUser(Long userId) {
        return assetMapper.selectDeletedWithoutProject().stream()
                .filter(asset -> canAccessAsset(asset, userId))
                .collect(Collectors.toList());
    }

    /**
     * 批量彻底删除无项目归属（project_id IS NULL）的历史软删资产。
     * <p>
     * 任一 id 不在历史软删行中或当前用户无权访问时整体失败，不做部分清理，
     * 避免误传 id 时静默删掉预期外的数据。
     *
     * @return 实际彻底删除的资产数量
     */
    @CacheEvict(value = { "asset", "assetItem" }, allEntries = true)
    @Transactional
    public int purgeDeletedOrphans(Long userId, List<Long> ids) {
        List<Long> distinctIds = ids == null ? List.of()
                : ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            throw new BusinessException("请提供要清理的历史软删资产 id");
        }
        List<Asset> orphans = assetMapper.selectDeletedByIds(distinctIds);
        if (orphans.size() != distinctIds.size()) {
            throw new BusinessException("部分资产不在无项目归属的历史软删行中，已取消批量清理");
        }
        for (Asset orphan : orphans) {
            if (!canAccessAsset(orphan, userId)) {
                throw new BusinessException(403, "无权清理资产: " + orphan.getId());
            }
        }
        orphans.forEach(this::purgeDeletedAsset);
        return orphans.size();
    }

    /**
     * 恢复回收站资产（置 deleted = 0），保留原 id 与引用关系；
     * 同步恢复其已软删的子资产——项目级联删除会连同子资产一起软删，
     * 只恢复主资产会让子资产永久不可达。
     */
    @CacheEvict(value = { "asset", "assetItem" }, allEntries = true)
    @Transactional
    public Asset restore(Long id) {
        if (assetMapper.restoreById(id) == 0) {
            throw new BusinessException("回收站中不存在该资产: " + id);
        }
        assetItemMapper.restoreDeletedByAssetId(id);
        return assetMapper.selectById(id);
    }

    /**
     * 彻底删除回收站资产：物理删除资产行及其全部子资产行，
     * 并清理行上引用的本地 /media 媒体文件（外链不属本系统产物，由门面忽略）。
     */
    @CacheEvict(value = { "asset", "assetItem" }, allEntries = true)
    @Transactional
    public void purge(Long id) {
        purgeDeletedAsset(getDeletedById(id));
    }

    /** 物理删除单个软删资产行及其子资产行，随后清理其媒体文件 */
    private void purgeDeletedAsset(Asset deleted) {
        List<String> mediaUrls = collectDeletedMediaUrls(deleted);
        assetItemMapper.deletePhysicallyByAssetId(deleted.getId());
        assetMapper.deletePhysicallyById(deleted.getId());
        mediaStorageService.deleteByMediaUrls(mediaUrls);
    }

    /** 收集软删资产及其全部子资产（含已软删子资产）引用的媒体 URL，供彻底删除后清理 */
    private List<String> collectDeletedMediaUrls(Asset deleted) {
        List<String> urls = new ArrayList<>();
        if (StrUtil.isNotBlank(deleted.getCoverUrl())) {
            urls.add(deleted.getCoverUrl());
        }
        for (AssetItem item : assetItemMapper.selectPhysicallyByAssetId(deleted.getId())) {
            if (StrUtil.isNotBlank(item.getImageUrl())) {
                urls.add(item.getImageUrl());
            }
            if (StrUtil.isNotBlank(item.getThumbnailUrl())) {
                urls.add(item.getThumbnailUrl());
            }
        }
        return urls;
    }

    // ========== 子资产 ==========

    public AssetItem getItemById(Long id) {
        AssetItem item = assetItemMapper.selectById(id);
        if (item == null)
            throw new BusinessException("子资产不存在: " + id);
        return item;
    }

    @Cacheable(value = "assetItem", key = "'asset:' + #assetId")
    public List<AssetItem> listItems(Long assetId) {
        return assetItemMapper.selectList(new LambdaQueryWrapper<AssetItem>()
                .eq(AssetItem::getAssetId, assetId)
                .orderByAsc(AssetItem::getSortOrder));
    }

    @CacheEvict(value = { "assetItem", "asset" }, allEntries = true)
    @Transactional
    public AssetItem createItem(AssetItem item) {
        validateAssetItemMediaUrls(item);
        assetItemMapper.insert(item);
        syncCoverIfAbsent(item);
        return item;
    }

    @CacheEvict(value = { "assetItem", "asset" }, allEntries = true)
    @Transactional
    public AssetItem updateItem(AssetItem item) {
        AssetItem existing = assetItemMapper.selectById(item.getId());
        if (existing == null)
            throw new BusinessException("子资产不存在: " + item.getId());
        validateAssetItemMediaUrls(item);
        assetItemMapper.updateById(item);
        // 部分更新时 item 可能缺少 assetId/imageUrl/itemType，用 existing 补全
        if (item.getAssetId() == null) {
            item.setAssetId(existing.getAssetId());
        }
        if (item.getImageUrl() == null) {
            item.setImageUrl(existing.getImageUrl());
        }
        if (item.getItemType() == null) {
            item.setItemType(existing.getItemType());
        }
        syncCoverIfAbsent(item);
        return item;
    }

    @CacheEvict(value = "assetItem", allEntries = true)
    @Transactional
    public void deleteItem(Long id) {
        assetItemMapper.deleteById(id);
    }

    private void validateAssetMediaUrls(Asset asset) {
        if (asset == null) {
            return;
        }
        rejectDataUrl(asset.getCoverUrl(), "coverUrl");
    }

    private void validateAssetItemMediaUrls(AssetItem item) {
        if (item == null) {
            return;
        }
        rejectDataUrl(item.getImageUrl(), "imageUrl");
        rejectDataUrl(item.getThumbnailUrl(), "thumbnailUrl");
    }

    private void rejectDataUrl(String rawUrl, String fieldName) {
        if (StrUtil.isNotBlank(rawUrl) && StrUtil.startWithIgnoreCase(rawUrl.trim(), "data:")) {
            throw new BusinessException(fieldName + " 不支持 base64，请先调用 /api/storage/upload 上传二进制文件");
        }
    }

    private void applyCurrentTeamOwnership(Asset asset) {
        Long creatorUserId = asset.getUserId() != null ? asset.getUserId() : SecurityUtils.getCurrentUserId();
        if (creatorUserId == null) {
            return;
        }
        asset.setUserId(creatorUserId);
        TeamService.OwnerScope ownerScope = teamService.getRequiredCurrentOwnerScopeByUser(creatorUserId);
        asset.setOwnerType(ownerScope.getOwnerType());
        asset.setOwnerId(ownerScope.getOwnerId());
    }

    /**
     * 同步主资产封面：
     * - initial 类型子资产：新增或更新图片时，始终同步为主资产封面
     * - 其他类型子资产：仅在主资产无封面时自动填充
     */
    private void syncCoverIfAbsent(AssetItem item) {
        if (StrUtil.isBlank(item.getImageUrl()) || item.getAssetId() == null) {
            return;
        }
        Asset asset = assetMapper.selectById(item.getAssetId());
        if (asset == null) {
            return;
        }
        if ("initial".equals(item.getItemType())) {
            asset.setCoverUrl(item.getImageUrl());
            assetMapper.updateById(asset);
        } else if (StrUtil.isBlank(asset.getCoverUrl())) {
            asset.setCoverUrl(item.getImageUrl());
            assetMapper.updateById(asset);
        }
    }
}
