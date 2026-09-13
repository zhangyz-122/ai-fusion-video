import GenerationWorkbench from "@/components/dashboard/generation-workbench";

export default async function GenerateImagePage({ searchParams }: { searchParams: Promise<{ modelId?: string }> }) {
  const { modelId } = await searchParams;
  const initialModelId = modelId && /^\d+$/.test(modelId) ? Number(modelId) : undefined;
  return <GenerationWorkbench mode="image" initialModelId={initialModelId} />;
}
