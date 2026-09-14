"use client";

/**
 * 移动端软键盘弹出时,保证正在聚焦的输入控件滚动到可视区域。
 *
 * 浏览器通常会滚动最近的可滚动祖先,但键盘弹出有 100~300ms 的动画窗口,
 * 过早计算会得到错误的可视区域;这里在 focusin 后延迟补偿滚动一次,
 * 并在键盘高度变化(visualViewport resize)时对最后一个聚焦控件再滚动一次。
 * 仅在窄屏(<1024px)安装;桌面端完全无行为。
 */
export function installMobileInputScrollIntoView(): () => void {
  if (typeof window === "undefined") return () => {};
  const media = window.matchMedia("(max-width: 1023px)");
  if (!media.matches) return () => {};

  let timer: number | null = null;
  let lastTarget: HTMLElement | null = null;

  const isTextEntry = (el: EventTarget | null): el is HTMLElement => {
    if (!(el instanceof Element)) return false;
    const tag = el.tagName;
    return tag === "INPUT" || tag === "TEXTAREA" || (el as HTMLElement).isContentEditable;
  };

  const scrollTargetIntoView = () => {
    if (!lastTarget || !lastTarget.isConnected) return;
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    lastTarget.scrollIntoView({
      block: "nearest",
      behavior: reduceMotion ? "auto" : "smooth",
    });
  };

  const scheduleScroll = () => {
    if (timer !== null) window.clearTimeout(timer);
    timer = window.setTimeout(() => {
      timer = null;
      scrollTargetIntoView();
    }, 260);
  };

  const onFocusIn = (event: FocusEvent) => {
    if (!isTextEntry(event.target)) return;
    lastTarget = event.target;
    scheduleScroll();
  };

  const onVisualViewportResize = () => {
    if (!lastTarget) return;
    scheduleScroll();
  };

  document.addEventListener("focusin", onFocusIn, true);
  window.visualViewport?.addEventListener("resize", onVisualViewportResize);
  return () => {
    document.removeEventListener("focusin", onFocusIn, true);
    window.visualViewport?.removeEventListener("resize", onVisualViewportResize);
    if (timer !== null) window.clearTimeout(timer);
  };
}
