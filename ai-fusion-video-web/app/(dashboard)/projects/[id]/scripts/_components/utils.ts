import type { SceneItem, DialogueElement } from "@/lib/api/script";

export function parseDialogues(scene: SceneItem): DialogueElement[] {
  if (!scene.dialogues) return [];
  try {
    const parsed =
      typeof scene.dialogues === "string"
        ? JSON.parse(scene.dialogues)
        : scene.dialogues;
    if (!Array.isArray(parsed)) return [];
    // 历史自动分块产物使用 {speaker, line} 结构（旧数据不做迁移），渲染前统一映射为标准结构
    return parsed.map((item): DialogueElement => {
      const legacy = item as Record<string, unknown>;
      if ("speaker" in legacy || "line" in legacy) {
        return {
          type: 1,
          character_name:
            typeof legacy.speaker === "string" ? legacy.speaker : undefined,
          content: typeof legacy.line === "string" ? legacy.line : "",
        };
      }
      return item as DialogueElement;
    });
  } catch {
    return [];
  }
}

export function parseCharacters(scene: SceneItem): string[] {
  if (!scene.characters) return [];
  try {
    return typeof scene.characters === "string"
      ? JSON.parse(scene.characters)
      : scene.characters;
  } catch {
    return [];
  }
}
