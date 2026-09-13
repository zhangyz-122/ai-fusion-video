"use client";

import { useRouter } from "next/navigation";
import {
  Info,
  Camera,
  Clock,
  Film,
  Hash,
  Image as ImageIcon,
  MessageSquare,
  Move3d,
  Music,
  Volume2,
  FileText,
  Loader2,
  Users,
  ZoomIn,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { resolveMediaUrl } from "@/lib/api/client";
import { SafeImage } from "@/components/ui/safe-image";

import type { Asset, AssetItem } from "@/lib/api/asset";
import type { Project } from "@/lib/api/project";
import type { StoryboardItem, StoryboardFrameType } from "@/lib/api/storyboard";
import { FrameReferenceSection } from "../storyboard-frame-reference-dialog";
import { LinkedAssetGroup } from "./linked-asset-group";
import { typeConfig } from "./shared";
import { useItemLinkedAssets } from "./use-item-linked-assets";

// ========== 镜头详情（保留原有） ==========

export function ItemDetail({
  item,
  projectId,
  project,
  assetLookup,
  onEditAssets,
  onUpdateFrame,
  onGenerateFrame,
  onPreviewImage,
}: {
  item: StoryboardItem;
  projectId: number;
  project?: Project | null;
  assetLookup?: Record<number, { item: AssetItem; asset: Asset }>;
  onEditAssets?: () => void;
  onUpdateFrame?: (itemId: number, frameType: StoryboardFrameType, imageUrl: string | null) => Promise<void> | void;
  onGenerateFrame?: (item: StoryboardItem, frameType: StoryboardFrameType, prompt: string) => Promise<void> | void;
  onPreviewImage?: (url: string, title: string) => void;
}) {
  const router = useRouter();
  const detailRows: {
    icon: typeof Info;
    label: string;
    value: string | null;
  }[] = [
    { icon: Hash, label: "镜号", value: item.shotNumber },
    { icon: Camera, label: "景别", value: item.shotType },
    {
      icon: Clock,
      label: "时长",
      value: item.duration ? `${item.duration}s` : null,
    },
    { icon: Move3d, label: "镜头运动", value: item.cameraMovement },
    { icon: Camera, label: "机位角度", value: item.cameraAngle },
    { icon: Camera, label: "焦距", value: item.focalLength },
    { icon: Film, label: "转场", value: item.transition },
  ];

  // ===== 加载镜头关联资产 =====
  const { linkedAssets, assetsLoading } = useItemLinkedAssets({ item, assetLookup });

  const hasLinkedAssets =
    linkedAssets.characters.length > 0 ||
    linkedAssets.scenes.length > 0 ||
    linkedAssets.props.length > 0;

  return (
    <div className="p-4 space-y-5">
      {/* 标题 */}
      <div>
        <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3 flex items-center gap-1.5">
          <Info className="h-3 w-3" /> 镜头详情
        </h4>
      </div>

      <FrameReferenceSection
        item={item}
        project={project}
        frameType="first"
        imageUrl={item.firstFrameImageUrl}
        prompt={item.firstFramePrompt}
        onUpdateFrame={onUpdateFrame}
        onGenerateFrame={onGenerateFrame}
        onPreviewImage={onPreviewImage}
      />

      <FrameReferenceSection
        item={item}
        project={project}
        frameType="last"
        imageUrl={item.lastFrameImageUrl}
        prompt={item.lastFramePrompt}
        onUpdateFrame={onUpdateFrame}
        onGenerateFrame={onGenerateFrame}
        onPreviewImage={onPreviewImage}
      />

      {/* 预览图 */}
      {(item.imageUrl ||
        item.referenceImageUrl ||
        item.generatedImageUrl) && (
        <div
          onClick={() => {
            const rawUrl = item.generatedImageUrl || item.imageUrl || item.referenceImageUrl;
            if (rawUrl && onPreviewImage) {
              onPreviewImage(rawUrl, `镜头 #${item.shotNumber || item.autoShotNumber || ""} 画面`);
            }
          }}
          className={cn(
            "rounded-lg overflow-hidden border border-border/20 relative group/preview cursor-zoom-in hover:border-primary/40 transition-colors"
          )}
        >
          <SafeImage
            src={
              resolveMediaUrl(item.generatedImageUrl ||
                item.imageUrl ||
                item.referenceImageUrl)
            }
            alt="镜头画面"
            fallbackType="image"
            className="w-full aspect-video object-cover transition-transform group-hover/preview:scale-102"
          />
          <div className="absolute inset-0 bg-black/0 group-hover/preview:bg-black/25 flex items-center justify-center opacity-0 group-hover/preview:opacity-100 transition-all">
            <ZoomIn className="h-5 w-5 text-white/90" />
          </div>
        </div>
      )}

      {/* 基础属性 */}
      <div className="space-y-2">
        {detailRows.map(
          ({ icon: AttrIcon, label, value }) =>
            value && (
              <div key={label} className="flex items-center gap-2 text-xs">
                <AttrIcon className="h-3 w-3 text-muted-foreground shrink-0" />
                <span className="text-muted-foreground">{label}</span>
                <span className="font-medium ml-auto truncate max-w-[140px]">
                  {value}
                </span>
              </div>
            )
        )}
      </div>

      {/* 画面内容 */}
      {item.content && (
        <div className="border-t border-border/20 pt-4">
          <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2 flex items-center gap-1.5">
            <ImageIcon className="h-3 w-3" /> 画面内容
          </h4>
          <p className="text-xs text-foreground/80 leading-relaxed">
            {item.content}
          </p>
        </div>
      )}

      {/* 场景预期 */}
      {item.sceneExpectation && (
        <div className="border-t border-border/20 pt-4">
          <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2">
            场景预期
          </h4>
          <p className="text-xs text-muted-foreground leading-relaxed italic">
            {item.sceneExpectation}
          </p>
        </div>
      )}

      {/* 对白 / 旁白 */}
      {item.dialogue && (
        <div className="border-t border-border/20 pt-4">
          <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2 flex items-center gap-1.5">
            <MessageSquare className="h-3 w-3" /> 对白 / 旁白
          </h4>
          <p className="text-xs text-foreground/80 leading-relaxed italic">
            「{item.dialogue}」
          </p>
        </div>
      )}

      {/* 音效 & 音乐 */}
      {(item.soundEffect || item.music || item.sound) && (
        <div className="border-t border-border/20 pt-4">
          <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2 flex items-center gap-1.5">
            <Volume2 className="h-3 w-3" /> 声音
          </h4>
          <div className="space-y-1.5">
            {item.sound && (
              <div className="flex items-center gap-1.5 text-xs">
                <span className="text-muted-foreground">声音:</span>
                <span>{item.sound}</span>
              </div>
            )}
            {item.soundEffect && (
              <div className="flex items-center gap-1.5 text-xs">
                <span className="text-muted-foreground">音效:</span>
                <span>{item.soundEffect}</span>
              </div>
            )}
            {item.music && (
              <div className="flex items-center gap-1.5 text-xs">
                <Music className="h-3 w-3 text-muted-foreground shrink-0" />
                <span>{item.music}</span>
              </div>
            )}
          </div>
        </div>
      )}

      {/* 参考图 */}
      {item.referenceImageUrl && (
        <div className="border-t border-border/20 pt-4">
          <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2 flex items-center gap-1.5">
            <ImageIcon className="h-3 w-3" /> 参考图
          </h4>
          <div className="rounded-lg overflow-hidden border border-border/20">
            <SafeImage
              src={resolveMediaUrl(item.referenceImageUrl)}
              alt="参考图"
              fallbackType="image"
              className="w-full aspect-video object-cover"
            />
          </div>
        </div>
      )}

      {/* 备注 */}
      {item.remark && (
        <div className="border-t border-border/20 pt-4">
          <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2 flex items-center gap-1.5">
            <FileText className="h-3 w-3" /> 备注
          </h4>
          <p className="text-xs text-muted-foreground leading-relaxed">
            {item.remark}
          </p>
        </div>
      )}

      {/* ===== 关联资产 ===== */}
      {assetsLoading && (
        <div className="border-t border-border/20 pt-4 flex items-center justify-center py-4">
          <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
        </div>
      )}

      {!assetsLoading && (
        <div className="border-t border-border/20 pt-4 space-y-4">
          <div className="flex items-center justify-between">
            <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
              <Users className="h-3 w-3" /> 关联资产
            </h4>
            {onEditAssets && (
              <button
                onClick={onEditAssets}
                className="text-[10px] text-primary hover:underline font-medium"
              >
                编辑关联
              </button>
            )}
          </div>

          {!hasLinkedAssets ? (
            <p className="text-[11px] text-muted-foreground/50 italic pl-1">
              暂无镜头关联资产，请点击右上角编辑关联
            </p>
          ) : (
            (
              [
                ["character", linkedAssets.characters],
                ["scene", linkedAssets.scenes],
                ["prop", linkedAssets.props],
              ] as [keyof typeof typeConfig, (Asset & { items: AssetItem[] })[]][]
            ).map(
              ([type, assets]) =>
                assets.length > 0 && (
                  <LinkedAssetGroup
                    key={type}
                    type={type}
                    assets={assets}
                    onAssetClick={(id) =>
                      router.push(
                        `/projects/${projectId}/assets?highlight=${id}`
                      )
                    }
                    onPreviewImage={onPreviewImage}
                  />
                )
            )
          )}
        </div>
      )}
    </div>
  );
}
