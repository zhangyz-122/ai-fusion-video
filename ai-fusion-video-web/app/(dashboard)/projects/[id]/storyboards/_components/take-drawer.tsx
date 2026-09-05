'use client';

import { useState } from 'react';
import type { ProductionTake } from '@/lib/api/production';
import { selectTake } from '@/lib/api/production';

interface TakeDrawerProps {
  itemId: number;
  takes: ProductionTake[];
  selectedTakeId?: number;
  open: boolean;
  onClose: () => void;
  onSelected?: (takeId: number) => void;
}

export function TakeDrawer({ itemId, takes, selectedTakeId, open, onClose, onSelected }: TakeDrawerProps) {
  const [selecting, setSelecting] = useState<number | null>(null);
  if (!open) return null;

  const handleSelect = async (takeId: number) => {
    setSelecting(takeId);
    try {
      await selectTake(itemId, takeId);
      onSelected?.(takeId);
      onClose();
    } catch (e) {
      console.error('selectTake failed:', e);
    } finally {
      setSelecting(null);
    }
  };

  return (
    <div className="fixed inset-y-0 right-0 w-96 bg-white shadow-xl z-50 overflow-y-auto p-4">
      <div className="flex justify-between items-center mb-4">
        <h3 className="text-lg font-semibold">Takes</h3>
        <button onClick={onClose} className="text-gray-400 hover:text-gray-600">✕</button>
      </div>
      {takes.length === 0 && <p className="text-gray-400">No takes generated yet.</p>}
      <div className="space-y-3">
        {takes.map((take) => (
          <div key={take.id} className={`border rounded-lg p-3 ${take.id === selectedTakeId ? 'border-blue-500 bg-blue-50' : 'border-gray-200'}`}>
            <div className="flex justify-between items-center">
              <span className="text-sm font-medium">Take #{take.id}</span>
              <span className={`text-xs px-2 py-0.5 rounded ${
                take.qc_status === 'PASS' ? 'bg-green-100 text-green-700' :
                take.qc_status === 'FAIL' ? 'bg-red-100 text-red-700' :
                'bg-yellow-100 text-yellow-700'
              }`}>{take.qc_status}</span>
            </div>
            {take.metadata_json?.videoUrl && (
              <video src={take.metadata_json.videoUrl} controls className="mt-2 w-full rounded" />
            )}
            <div className="mt-2 text-xs text-gray-500 space-y-0.5">
              <p>Model: {take.model_id || '—'}</p>
              <p>Seed: {take.seed ?? '—'}</p>
              <p>Workflow Version: {take.workflow_version_id || '—'}</p>
            </div>
            {take.id === selectedTakeId ? (
              <span className="mt-2 inline-block text-xs text-blue-600 font-medium">✓ Selected</span>
            ) : take.qc_status !== 'FAIL' ? (
              <button
                onClick={() => handleSelect(take.id)}
                disabled={selecting === take.id}
                className="mt-2 w-full py-1.5 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
              >
                {selecting === take.id ? 'Selecting…' : 'Select this take'}
              </button>
            ) : (
              <p className="mt-2 text-xs text-red-500">QC FAIL — cannot select</p>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
