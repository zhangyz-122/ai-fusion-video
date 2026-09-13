package com.stonewu.fusion.service.ai.tool.asset;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.entity.asset.Asset;
import com.stonewu.fusion.entity.asset.AssetItem;
import com.stonewu.fusion.entity.project.Project;
import com.stonewu.fusion.service.ai.ToolExecutionContext;
import com.stonewu.fusion.service.ai.ToolExecutor;
import com.stonewu.fusion.service.asset.AssetService;
import com.stonewu.fusion.service.project.ProjectService;
import com.stonewu.fusion.service.system.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 项目资产列表工具（list_project_assets）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ListProjectAssetsToolExecutor implements ToolExecutor {

    private final AssetService assetService;
    private final ProjectService projectService;
    private final SystemConfigService systemConfigService;

    @Override
    public String getToolName() {
        return "list_project_assets";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String getDisplayName() {
        return "列出资产";
    }

    @Override
    public String getToolDescription() {
        return """
                列出项目下的资产列表（含子资产信息）。
                支持的资产类型：character（角色）、scene（场景）、prop（道具）
                type 为可选筛选项；省略、传 all/全部/所有或传中文类型时，工具会自动规范化。

                使用场景：
                - 创建资产前先查看已有资产，避免重复创建
                - 查看角色/场景/道具的详细信息和图片

                如果提供了 projectId，则返回该项目下的资产（需有权限）。
                如果没有 projectId，则返回当前用户可访问的资产。
                """;
    }

    @Override
    public String getParametersSchema() {
        return """
                {
                    "type": "object",
                    "properties": {
                        "projectId": {
                            "type": "integer",
                            "description": "项目ID（可选）。不传则返回当前可访问的资产"
                        },
                        "type": {
                            "type": "string",
                            "description": "资产类型筛选（可选）：character/角色、scene/场景、prop/道具；all/全部表示返回全部"
                        }
                    },
                    "required": []
                }
                """;
    }

    @Override
    public String execute(String toolInput, ToolExecutionContext context) {
        try {
            JSONObject params = JSONUtil.parseObj(toolInput);
            Long projectId = params.getLong("projectId");
            String requestedType = params.getStr("type");
            String type = normalizeType(requestedType);
            Long userId = context.getUserId();

            List<Asset> assets;
            if (projectId != null) {
                if (!projectService.canAccessProject(projectId, userId)) {
                    return JSONUtil.createObj().set("status", "error")
                            .set("message", "无权访问该项目").toString();
                }
                assets = assetService.listByProject(projectId, type, null);
            } else {
                assets = assetService.listAccessibleByUser(userId, type);
            }

            JSONArray resultArray = new JSONArray();
            for (Asset asset : assets) {
                List<AssetItem> items = assetService.listItems(asset.getId());
                JSONArray itemsArray = new JSONArray();
                for (AssetItem item : items) {
                    itemsArray.add(JSONUtil.createObj()
                            .set("id", item.getId())
                            .set("itemType", item.getItemType())
                            .set("name", item.getName())
                            .set("imageUrl", resolvePublicUrl(item.getImageUrl()))
                            .set("thumbnailUrl", resolvePublicUrl(item.getThumbnailUrl())));
                }

                resultArray.add(JSONUtil.createObj()
                        .set("id", asset.getId())
                        .set("name", asset.getName())
                        .set("type", asset.getType())
                        .set("description", asset.getDescription())
                        .set("coverUrl", resolvePublicUrl(asset.getCoverUrl()))
                        .set("itemCount", items.size())
                        .set("items", itemsArray));
            }

            return JSONUtil.createObj()
                    .set("type", type != null ? type : "all")
                    .set("requestedType", requestedType)
                    .set("total", assets.size())
                    .set("assets", resultArray).toString();
        } catch (Exception e) {
            log.error("查询资产列表失败", e);
            return JSONUtil.createObj().set("status", "error").set("message", "查询失败: " + e.getMessage()).toString();
        }
    }

    private String resolvePublicUrl(String resourceUrl) {
        if (resourceUrl == null || resourceUrl.isBlank()) {
            return null;
        }
        String publicUrl = systemConfigService.resolvePublicUrl(resourceUrl);
        return publicUrl != null ? publicUrl : resourceUrl;
    }

    /** 将模型常用的中文或“全部”表达统一为数据库筛选值。未知筛选值安全回退为全部。 */
    private String normalizeType(String requestedType) {
        if (requestedType == null || requestedType.isBlank()) {
            return null;
        }
        String normalized = requestedType.trim().toLowerCase();
        return switch (normalized) {
            case "character", "角色", "人物" -> "character";
            case "scene", "场景", "环境" -> "scene";
            case "prop", "道具", "物品" -> "prop";
            case "all", "全部", "所有", "资产", "assets" -> null;
            default -> {
                log.warn("list_project_assets received unsupported type='{}'; falling back to all", requestedType);
                yield null;
            }
        };
    }
}
