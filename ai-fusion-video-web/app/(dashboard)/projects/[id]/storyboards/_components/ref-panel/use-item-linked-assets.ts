"use client";

import { useCallback, useEffect, useState } from "react";
import { toastApiError } from "@/lib/api/toast-api-error";
import { parseIds } from "@/lib/storyboard-item-utils";
import { assetApi } from "@/lib/api/asset";

import type { Asset, AssetItem } from "@/lib/api/asset";
import type { StoryboardItem } from "@/lib/api/storyboard";

export type LinkedAssets = {
  characters: (Asset & { items: AssetItem[] })[];
  scenes: (Asset & { items: AssetItem[] })[];
  props: (Asset & { items: AssetItem[] })[];
};

/** 加载镜头关联资产:优先使用 assetLookup 本地索引,缺失时回退接口批量查询 */
export function useItemLinkedAssets({
  item,
  assetLookup,
}: {
  item: StoryboardItem;
  assetLookup?: Record<number, { item: AssetItem; asset: Asset }>;
}) {
  const [linkedAssets, setLinkedAssets] = useState<LinkedAssets>({
    characters: [],
    scenes: [],
    props: [],
  });
  const [assetsLoading, setAssetsLoading] = useState(false);

  const loadLinkedAssets = useCallback(async () => {
    // 解析各类子资产 ID
    const charItemIds = parseIds(item.characterIds);
    const sceneItemId = item.sceneAssetItemId && item.sceneAssetItemId > 0 ? item.sceneAssetItemId : null;
    const propItemIds = parseIds(item.propIds);

    const allItemIds = [...charItemIds, ...propItemIds];
    if (sceneItemId) allItemIds.push(sceneItemId);

    if (allItemIds.length === 0) {
      setLinkedAssets({ characters: [], scenes: [], props: [] });
      return;
    }

    setAssetsLoading(true);
    try {
      // 批量获取子资产详情
      const items = await Promise.all(
        allItemIds.map((id) => assetApi.getItem(id).catch((error) => {
          toastApiError(error, "加载镜头关联资产项失败");
          return null;
        }))
      );

      // 收集主资产 ID（去重）
      const parentIds = new Set<number>();
      const itemMap = new Map<number, AssetItem>();
      for (const r of items) {
        if (!r) continue;
        itemMap.set(r.id, r);
        if (r.assetId) parentIds.add(r.assetId);
      }

      // 批量获取主资产 + 其子资产列表
      const parentResults = await Promise.all(
        Array.from(parentIds).map(async (id) => {
          try {
            const [asset, subItems] = await Promise.all([
              assetApi.get(id),
              assetApi.listItems(id),
            ]);
            return { ...asset, items: subItems || [] };
          } catch {
            return null;
          }
        })
      );

      const valid = parentResults.filter(
        (r): r is Asset & { items: AssetItem[] } => r !== null
      );

      // 按子资产ID → 主资产分类
      const charParentIds = new Set(charItemIds.map((id) => itemMap.get(id)?.assetId).filter((id): id is number => id != null));
      const sceneParentId = sceneItemId ? itemMap.get(sceneItemId)?.assetId : null;
      const propParentIds = new Set(propItemIds.map((id) => itemMap.get(id)?.assetId).filter((id): id is number => id != null));

      setLinkedAssets({
        characters: valid.filter((a) => charParentIds.has(a.id)),
        scenes: valid.filter((a) => a.id === sceneParentId),
        props: valid.filter((a) => propParentIds.has(a.id)),
      });
    } catch (err) {
      console.error("加载镜头关联资产失败:", err);
      toastApiError(err, "加载镜头关联资产失败");
    } finally {
      setAssetsLoading(false);
    }
  }, [item.characterIds, item.sceneAssetItemId, item.propIds]);

  useEffect(() => {
    if (assetLookup && Object.keys(assetLookup).length > 0) {
      const charItemIds = parseIds(item.characterIds);
      const sceneItemId = item.sceneAssetItemId && item.sceneAssetItemId > 0 ? item.sceneAssetItemId : null;
      const propItemIds = parseIds(item.propIds);

      const charsMap = new Map<number, Asset & { items: AssetItem[] }>();
      charItemIds.forEach(id => {
        const entry = assetLookup[id];
        if (entry) {
          const { item: subItem, asset } = entry;
          if (!charsMap.has(asset.id)) {
            charsMap.set(asset.id, { ...asset, items: [] });
          }
          if (!charsMap.get(asset.id)!.items.some(x => x.id === subItem.id)) {
            charsMap.get(asset.id)!.items.push(subItem);
          }
        }
      });

      const scenesList: (Asset & { items: AssetItem[] })[] = [];
      if (sceneItemId) {
        const entry = assetLookup[sceneItemId];
        if (entry) {
          const { item: subItem, asset } = entry;
          scenesList.push({ ...asset, items: [subItem] });
        }
      }

      const propsMap = new Map<number, Asset & { items: AssetItem[] }>();
      propItemIds.forEach(id => {
        const entry = assetLookup[id];
        if (entry) {
          const { item: subItem, asset } = entry;
          if (!propsMap.has(asset.id)) {
            propsMap.set(asset.id, { ...asset, items: [] });
          }
          if (!propsMap.get(asset.id)!.items.some(x => x.id === subItem.id)) {
            propsMap.get(asset.id)!.items.push(subItem);
          }
        }
      });

      setLinkedAssets({
        characters: Array.from(charsMap.values()),
        scenes: scenesList,
        props: Array.from(propsMap.values()),
      });
      setAssetsLoading(false);
    } else {
      loadLinkedAssets();
    }
  }, [item.characterIds, item.sceneAssetItemId, item.propIds, assetLookup, loadLinkedAssets]);

  return { linkedAssets, assetsLoading };
}
