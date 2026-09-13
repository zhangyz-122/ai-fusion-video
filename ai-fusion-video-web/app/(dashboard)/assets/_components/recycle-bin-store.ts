import type { Asset } from "@/lib/api/asset";

// ============================================================
// 回收站本地存储
//
// 后端对资产使用 MyBatis-Plus @TableLogic 逻辑删除，既有接口
// 查询/更新均自动追加 deleted = 0，无法列出或恢复已删除行。
// 回收站因此由前端组合实现：删除前在本页快照资产，恢复时通过
// 创建接口重建（获得新 id）。彻底删除仅移除本地快照，数据库中
// 的软删记录待后端受控清理能力落地后统一处理。
// ============================================================

const STORAGE_KEY = "rg:assets:recycle-bin:v1";

/** 回收站中的一条删除记录 */
export interface DeletedAssetRecord {
  /** 原资产 id，作为记录主键 */
  assetId: number;
  /** 执行删除的用户 id（不同账号隔离） */
  userId: number;
  /** 删除时间（ISO 字符串） */
  deletedAt: string;
  /** 删除前的完整资产快照 */
  snapshot: Asset;
}

function isBrowser(): boolean {
  return typeof window !== "undefined";
}

function readStore(): DeletedAssetRecord[] {
  if (!isBrowser()) return [];
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(
      (item): item is DeletedAssetRecord =>
        !!item &&
        typeof item === "object" &&
        typeof (item as DeletedAssetRecord).assetId === "number" &&
        typeof (item as DeletedAssetRecord).userId === "number" &&
        !!(item as DeletedAssetRecord).snapshot,
    );
  } catch {
    return [];
  }
}

function writeStore(records: DeletedAssetRecord[]): void {
  if (!isBrowser()) return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(records));
  } catch {
    // 存储不可用（隐私模式/容量不足）时静默降级为不记录
  }
}

/** 读取指定用户的回收站记录，按删除时间倒序 */
export function listDeletedAssets(userId: number): DeletedAssetRecord[] {
  return readStore()
    .filter((r) => r.userId === userId)
    .sort((a, b) => b.deletedAt.localeCompare(a.deletedAt));
}

/** 删除成功后记录快照；重复删除同一 id 时覆盖旧记录 */
export function recordDeletedAsset(userId: number, asset: Asset): void {
  const records = readStore().filter((r) => r.assetId !== asset.id);
  records.push({
    assetId: asset.id,
    userId,
    deletedAt: new Date().toISOString(),
    snapshot: asset,
  });
  writeStore(records);
}

/** 从回收站移除记录（恢复成功或彻底删除） */
export function removeDeletedAsset(userId: number, assetId: number): void {
  writeStore(
    readStore().filter((r) => !(r.userId === userId && r.assetId === assetId)),
  );
}
