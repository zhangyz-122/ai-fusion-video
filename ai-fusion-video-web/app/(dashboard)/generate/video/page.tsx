import GenerationWorkbench from "@/components/dashboard/generation-workbench";

export default async function GenerateVideoPage({ searchParams }: { searchParams: Promise<{ modelId?: string }> }) {
  const { modelId } = await searchParams;
  const initialModelId = modelId && /^\d+$/.test(modelId) ? Number(modelId) : undefined;
  return <GenerationWorkbench mode="video" initialModelId={initialModelId} />;
}
