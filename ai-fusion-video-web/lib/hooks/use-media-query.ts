"use client";

import { useCallback, useSyncExternalStore } from "react";

/**
 * 响应式媒体查询 Hook。
 * SSR 与首屏渲染返回 defaultValue,挂载后同步真实匹配结果,
 * 用于布局层按断点条件挂载互斥组件(如桌面顶栏 / 移动顶栏)。
 */
export function useMediaQuery(query: string, defaultValue = false): boolean {
  const subscribe = useCallback(
    (onStoreChange: () => void) => {
      const mql = window.matchMedia(query);
      mql.addEventListener("change", onStoreChange);
      return () => {
        mql.removeEventListener("change", onStoreChange);
      };
    },
    [query]
  );

  const getSnapshot = useCallback(() => window.matchMedia(query).matches, [query]);
  const getServerSnapshot = useCallback(() => defaultValue, [defaultValue]);

  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}
