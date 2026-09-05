'use client';

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { getProductionRuns, type ProductionRun } from '@/lib/api/production';

export default function ProductionOverviewPage() {
  const params = useParams<{ id: string }>();
  const projectId = Number(params.id);
  const [runs, setRuns] = useState<ProductionRun[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getProductionRuns(projectId).then(setRuns).catch(console.error).finally(() => setLoading(false));
  }, [projectId]);

  return (
    <div className="p-6 space-y-6">
      <h1 className="text-2xl font-bold">Production Overview</h1>
      {loading ? <p>Loading…</p> : (
        <div className="space-y-4">
          {runs.map((run) => (
            <div key={run.id} className="border rounded-lg p-4">
              <p className="font-medium">Run #{run.id} — {run.run_type}</p>
              <p className="text-sm text-gray-500">Status: {run.status}</p>
            </div>
          ))}
          {runs.length === 0 && <p className="text-gray-400">No production runs yet.</p>}
        </div>
      )}
    </div>
  );
}
