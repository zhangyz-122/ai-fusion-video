"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { Loader2 } from "lucide-react";
import { projectApi } from "@/lib/api/project";
import { type Script } from "@/lib/api/script";
import { toastApiError } from "@/lib/api/toast-api-error";
import { useProject } from "../project-context";
import { SourceImportDialog } from "./_components/source-import-dialog";
import { SourceTextCard } from "./_components/source-text-card";
import { StoryToScriptPanel } from "./_components/story-to-script-panel";

export default function ProjectSourcePage() {
  const params = useParams();
  const projectId = Number(params.id);
  const { project } = useProject();
  const [script, setScript] = useState<Script | null>(null);
  const [episodeCount, setEpisodeCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const overview = await projectApi.getWorkspaceOverview(projectId);
      setScript(overview.script);
      setEpisodeCount(overview.scriptEpisodeCount ?? 0);
    } catch (error) {
      toastApiError(error, "加载原文失败");
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return (
      <div className="flex justify-center py-24">
        <Loader2 className="animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!script) {
    return (
      <div className="mx-auto max-w-3xl px-6 py-16 text-center text-sm text-muted-foreground">
        还没有剧本容器。先打开「这部剧」初始化工作区。
      </div>
    );
  }

  const raw = script.rawContent ?? "";

  return (
    <div className="mx-auto w-full max-w-4xl space-y-6 px-6 py-6">
      <header className="space-y-2">
        <p className="text-sm text-muted-foreground">写 · 原文</p>
        <h1 className="text-2xl font-semibold tracking-tight">小说 / 故事原文</h1>
        <p className="text-sm text-muted-foreground">先保存原文，再转成剧本。转换结果在「写 · 剧本」里改。</p>
      </header>

      <SourceTextCard
        rawContent={script.rawContent}
        updateTime={script.updateTime}
        onEdit={() => setEditing(true)}
      />

      {raw.trim() && (
        <StoryToScriptPanel
          projectId={projectId}
          scriptId={script.id}
          rawContent={raw}
          episodeCount={episodeCount}
          projectName={project?.name}
          onConverted={() => void load()}
        />
      )}

      <SourceImportDialog
        open={editing}
        scriptId={script.id}
        initialContent={raw}
        episodeCount={episodeCount}
        onClose={() => setEditing(false)}
        onSaved={async (next) => {
          setScript(next);
          setEditing(false);
          await load();
        }}
      />
    </div>
  );
}
