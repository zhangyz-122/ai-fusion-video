'use client';

import { useState, useCallback } from 'react';
import type { ProductionTake } from '@/lib/api/production';
import { selectTake } from '@/lib/api/production';

interface TakeDrawerProps {
  itemId: number;
  takes: ProductionTake[];
  selectedTakeId?: number;
  open: boolean;
  onClose: () => void;
  onSelected?: (takeId: number) => void;
  onError?: (message: string) => void;
}

type ConfirmState = { takeId: number; action: 'select' } | null;

export function TakeDrawer({ itemId, takes, selectedTakeId, open, onClose, onSelected, onError }: TakeDrawerProps) {
  const [selecting, setSelecting] = useState<number | null>(null);
  const [confirm, setConfirm] = useState<ConfirmState>(null);
  const [error, setError] = useState<string | null>(null);

  const handleConfirmSelect = useCallback(async (takeId: number) => {
    setSelecting(takeId);
    setError(null);
    try {
      await selectTake(itemId, takeId);
      onSelected?.(takeId);
      setConfirm(null);
      onClose();
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setError(msg);
      onError?.(msg);
    } finally {
      setSelecting(null);
    }
  }, [itemId, onSelected, onClose, onError]);

  if (!open) return null;

  const qcBadge = (status: string) => {
    const map: Record<string, string> = {
      PASS: 'bg-green-100 text-green-800 border-green-300',
      FAIL: 'bg-red-100 text-red-800 border-red-300',
      REVIEW_REQUIRED: 'bg-yellow-100 text-yellow-800 border-yellow-300',
      PENDING: 'bg-gray-100 text-gray-600 border-gray-300',
    };
    return map[status] || 'bg-gray-100 text-gray-600 border-gray-300';
  };

  return (
    <>
      {/* Overlay */}
      <div className="fixed inset-0 bg-black/30 z-40" onClick={onClose} />
      {/* Drawer panel */}
      <div className="fixed inset-y-0 right-0 w-[420px] bg-white shadow-2xl z-50 flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200">
          <div>
            <h3 className="text-lg font-semibold text-gray-900">Production Takes</h3>
            <p className="text-xs text-gray-500">Item #{itemId} · {takes.length} take(s)</p>
          </div>
          <button onClick={onClose} className="p-1 rounded hover:bg-gray-100" aria-label="Close">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Error banner */}
        {error && (
          <div className="mx-4 mt-2 px-3 py-2 bg-red-50 border border-red-200 rounded text-sm text-red-700">
            {error}
          </div>
        )}

        {/* Takes list */}
        <div className="flex-1 overflow-y-auto px-4 py-3 space-y-4">
          {takes.length === 0 && (
            <div className="text-center py-12 text-gray-400">
              <p className="text-sm">No takes generated yet.</p>
              <p className="text-xs mt-1">Trigger a GENERATE_VIDEO step to produce takes.</p>
            </div>
          )}
          {takes.map((take) => {
            const isSelected = take.id === selectedTakeId;
            const isBlocked = take.qc_status === 'FAIL';
            const isBusy = selecting === take.id;
            return (
              <div key={take.id} className={`border rounded-xl overflow-hidden transition-colors ${
                isSelected ? 'border-blue-500 ring-2 ring-blue-200' :
                isBlocked ? 'border-red-200 opacity-80' : 'border-gray-200 hover:border-gray-300'
              }`}>
                {/* Video preview */}
                {take.metadata_json?.videoUrl && (
                  <video
                    src={take.metadata_json.videoUrl}
                    controls
                    preload="metadata"
                    className="w-full aspect-video bg-black"
                  />
                )}
                {!take.metadata_json?.videoUrl && (
                  <div className="aspect-video bg-gray-100 flex items-center justify-center">
                    <span className="text-gray-400 text-sm">No video preview</span>
                  </div>
                )}

                {/* Body */}
                <div className="p-3 space-y-2">
                  <div className="flex justify-between items-start">
                    <div>
                      <p className="text-sm font-semibold text-gray-900">Take #{take.id}</p>
                      <p className="text-xs text-gray-500">
                        {take.source_type === 'VIDEO_ITEM' ? 'Video' : 'Image'} · #{take.source_item_id}
                      </p>
                    </div>
                    <span className={`text-xs px-2 py-0.5 rounded border font-medium ${qcBadge(take.qc_status)}`}>
                      {take.qc_status}
                    </span>
                  </div>

                  {/* Metadata grid */}
                  <div className="grid grid-cols-2 gap-x-3 gap-y-1 text-xs text-gray-600">
                    <span className="font-medium">Model:</span><span>{take.model_id || '—'}</span>
                    <span className="font-medium">Seed:</span><span>{take.seed ?? '—'}</span>
                    <span className="font-medium">Workflow Version:</span><span>{take.workflow_version_id || '—'}</span>
                    {take.metadata_json?.duration != null && (
                      <><span className="font-medium">Duration:</span><span>{take.metadata_json.duration}s</span></>
                    )}
                  </div>

                  {/* QC FAIL blocking */}
                  {isBlocked && (
                    <div className="px-2 py-1.5 bg-red-50 rounded text-xs text-red-600">
                      ⛔ QC FAIL — selection blocked (override requires audit)
                    </div>
                  )}

                  {/* Selected indicator / action button */}
                  {isSelected && (
                    <div className="flex items-center gap-1.5 px-3 py-2 bg-blue-50 rounded-lg">
                      <svg className="w-4 h-4 text-blue-600" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                      </svg>
                      <span className="text-sm font-medium text-blue-700">Currently selected</span>
                    </div>
                  )}
                  {!isSelected && !isBlocked && (
                    <button
                      onClick={() => setConfirm({ takeId: take.id, action: 'select' })}
                      disabled={isBusy}
                      className="w-full py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                    >
                      {isBusy ? 'Selecting…' : 'Select this take'}
                    </button>
                  )}
                  {isBlocked && (
                    <button disabled className="w-full py-2 text-sm bg-gray-100 text-gray-400 rounded-lg cursor-not-allowed">
                      QC FAIL — selection blocked
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Confirmation dialog */}
      {confirm && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50">
          <div className="bg-white rounded-xl shadow-2xl p-6 max-w-sm w-full mx-4">
            <h4 className="text-lg font-semibold text-gray-900 mb-2">Confirm Take Selection</h4>
            <p className="text-sm text-gray-600 mb-4">
              Select Take #{confirm.takeId} for Item #{itemId}? This will update the storyboard's selected take.
            </p>
            <div className="flex gap-2 justify-end">
              <button onClick={() => setConfirm(null)} className="px-4 py-2 text-sm rounded-lg border border-gray-300 hover:bg-gray-50">Cancel</button>
              <button
                onClick={() => { handleConfirmSelect(confirm.takeId); setConfirm(null); }}
                disabled={selecting != null}
                className="px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                {selecting != null ? 'Selecting…' : 'Confirm'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
