package com.stonewu.fusion.controller.asset;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.controller.asset.vo.AssetCreateReqVO;
import com.stonewu.fusion.controller.asset.vo.AssetItemCreateReqVO;
import com.stonewu.fusion.controller.asset.vo.AssetItemUpdateReqVO;
import com.stonewu.fusion.controller.asset.vo.AssetUpdateReqVO;
import com.stonewu.fusion.convert.asset.AssetConvert;
import com.stonewu.fusion.entity.asset.Asset;
import com.stonewu.fusion.entity.asset.AssetItem;
import com.stonewu.fusion.service.asset.AssetService;
import com.stonewu.fusion.service.project.ProjectAccessGuard;
import com.stonewu.fusion.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.stonewu.fusion.service.asset.AssetMetadataRegistry;
import com.stonewu.fusion.service.asset.AssetMetadataRegistry.FieldDef;

import java.util.List;
import java.util.Map;

/**
 * 资产管理 Controller
 */
@Tag(name = "资产管理")
@RestController
@RequestMapping("/api/asset")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;
    private final ProjectAccessGuard accessGuard;

    // ========== 元数据 ==========

    @Operation(summary = "查询资产属性字段定义")
    @GetMapping("/metadata/{assetType}")
    public CommonResult<Map<String, Object>> getMetadata(@PathVariable String assetType) {
        List<FieldDef> fields = AssetMetadataRegistry.getFields(assetType);
        if (fields == null) {
            return CommonResult.error(400, "不支持的资产类型: " + assetType);
        }
        return CommonResult.success(Map.of(
                "assetType", assetType,
                "fields", fields
        ));
    }

    // ========== 资产 ==========

    @Operation(summary = "获取资产详情")
    @GetMapping("/{id}")
    public CommonResult<Asset> get(@PathVariable Long id) {
        accessGuard.assertAsset(id);
        return CommonResult.success(assetService.getById(id));
    }

    @Operation(summary = "按项目+类型查询资产列表")
    @GetMapping("/list")
    public CommonResult<List<Asset>> list(@RequestParam Long projectId,
                                          @RequestParam(required = false) String type,
                                          @RequestParam(required = false) String keyword) {
        accessGuard.assertProject(projectId);
        return CommonResult.success(assetService.listByProject(projectId, type, keyword));
    }

    @Operation(summary = "按项目查询资产及其所有子资产")
    @GetMapping("/list-with-items")
    public CommonResult<List<Map<String, Object>>> listWithItems(@RequestParam Long projectId) {
        accessGuard.assertProject(projectId);
        return CommonResult.success(assetService.listWithItemsByProject(projectId));
    }

    @Operation(summary = "分页查询当前用户的资产（跨项目），含类型统计")
    @GetMapping("/all")
    public CommonResult<Map<String, Object>> listAll(@RequestParam(required = false) Long projectId,
                                                      @RequestParam(required = false) String type,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        IPage<Asset> pageResult = assetService.pageAccessibleByUser(userId, projectId, type, keyword, page, size);
        Map<String, Long> typeCounts = assetService.countAccessibleByUserGroupByType(userId, null, null);
        return CommonResult.success(Map.of(
                "records", pageResult.getRecords(),
                "total", pageResult.getTotal(),
                "page", pageResult.getCurrent(),
                "size", pageResult.getSize(),
                "typeCounts", typeCounts
        ));
    }

    @Operation(summary = "创建资产")
    @PostMapping
    public CommonResult<Asset> create(@Valid @RequestBody AssetCreateReqVO reqVO) {
        accessGuard.assertProject(reqVO.getProjectId());
        Asset asset = AssetConvert.INSTANCE.convert(reqVO);
        // userId 由后端决定，owner 归属由 service 按当前团队绑定
        asset.setUserId(SecurityUtils.getCurrentUserId());
        return CommonResult.success(assetService.create(asset));
    }

    @Operation(summary = "更新资产")
    @PutMapping
    public CommonResult<Asset> update(@Valid @RequestBody AssetUpdateReqVO reqVO) {
        accessGuard.assertAsset(reqVO.getId());
        Asset asset = AssetConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(assetService.update(asset));
    }

    @Operation(summary = "删除资产")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        accessGuard.assertAsset(id);
        assetService.delete(id);
        return CommonResult.success(true);
    }

    // ========== 回收站（已逻辑删除资产） ==========

    @Operation(summary = "回收站：分页查询当前用户可访问项目内的已删除资产")
    @GetMapping("/recycle-bin")
    public CommonResult<Map<String, Object>> listRecycleBin(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityUtils.requireCurrentUserId();
        IPage<Asset> pageResult = assetService.pageDeletedInAccessibleProjects(userId, page, size);
        return CommonResult.success(Map.of(
                "records", pageResult.getRecords(),
                "total", pageResult.getTotal(),
                "page", pageResult.getCurrent(),
                "size", pageResult.getSize()
        ));
    }

    @Operation(summary = "回收站：恢复已删除资产（置 deleted = 0，保留原 id）")
    @PutMapping("/recycle-bin/{id}/restore")
    public CommonResult<Asset> restoreRecycled(@PathVariable Long id) {
        Asset deleted = assetService.getDeletedById(id);
        accessGuard.assertProject(deleted.getProjectId());
        return CommonResult.success(assetService.restore(deleted.getId()));
    }

    @Operation(summary = "回收站：彻底删除（物理删除）已删除资产")
    @DeleteMapping("/recycle-bin/{id}")
    public CommonResult<Boolean> purgeRecycled(@PathVariable Long id) {
        Asset deleted = assetService.getDeletedById(id);
        accessGuard.assertProject(deleted.getProjectId());
        assetService.purge(deleted.getId());
        return CommonResult.success(true);
    }

    // ========== 子资产 ==========

    @Operation(summary = "获取子资产详情")
    @GetMapping("/item/{id}")
    public CommonResult<AssetItem> getItem(@PathVariable Long id) {
        accessGuard.assertAssetItem(id);
        return CommonResult.success(assetService.getItemById(id));
    }

    @Operation(summary = "获取子资产列表")
    @GetMapping("/{assetId}/items")
    public CommonResult<List<AssetItem>> listItems(@PathVariable Long assetId) {
        accessGuard.assertAsset(assetId);
        return CommonResult.success(assetService.listItems(assetId));
    }

    @Operation(summary = "创建子资产")
    @PostMapping("/item")
    public CommonResult<AssetItem> createItem(@Valid @RequestBody AssetItemCreateReqVO reqVO) {
        accessGuard.assertAsset(reqVO.getAssetId());
        AssetItem item = AssetConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(assetService.createItem(item));
    }

    @Operation(summary = "更新子资产")
    @PutMapping("/item")
    public CommonResult<AssetItem> updateItem(@Valid @RequestBody AssetItemUpdateReqVO reqVO) {
        accessGuard.assertAssetItem(reqVO.getId());
        AssetItem item = AssetConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(assetService.updateItem(item));
    }

    @Operation(summary = "删除子资产")
    @DeleteMapping("/item/{id}")
    public CommonResult<Boolean> deleteItem(@PathVariable Long id) {
        accessGuard.assertAssetItem(id);
        assetService.deleteItem(id);
        return CommonResult.success(true);
    }
}
