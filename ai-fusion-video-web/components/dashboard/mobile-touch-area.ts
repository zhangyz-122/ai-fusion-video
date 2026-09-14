/**
 * 移动端(<1024px)触控热区扩展。
 *
 * 背景:共享按钮系统规定了 24/32/36/40px 的视觉尺寸,但手机触控目标需要 ≥44px。
 * 这里用透明伪元素把小控件的可点击区域扩大到约 44px,不改变控件视觉尺寸;
 * 仅在 `max-lg:`(<1024px)生效,桌面端布局与交互完全不变。
 *
 * 用法:与控件现有 className 合并,按控件的定位方式二选一:
 * - `touchHitArea.*`:常规文档流控件(自动补充 max-lg:relative 作为伪元素定位基准);
 * - `touchHitAreaOverlay.*`:已是 absolute/fixed 定位的控件(禁止再叠加 relative,
 *   否则会在移动端覆盖原定位方式,导致控件脱离原位置)。
 *
 * 注意:同一容器内相邻小控件的间距需 ≥ 两侧扩大量之和,避免热区互相重叠
 * (24px 控件相邻时间距至少 20px,可用 `max-lg:gap-5` / `max-lg:gap-2.5` 按布局选择)。
 */

const baseAfter = {
  size20: "max-lg:after:absolute max-lg:after:-inset-3 max-lg:after:rounded-lg max-lg:after:content-['']",
  size24: "max-lg:after:absolute max-lg:after:-inset-2.5 max-lg:after:rounded-lg max-lg:after:content-['']",
  size28: "max-lg:after:absolute max-lg:after:-inset-2 max-lg:after:rounded-lg max-lg:after:content-['']",
  size32: "max-lg:after:absolute max-lg:after:-inset-1.5 max-lg:after:rounded-xl max-lg:after:content-['']",
  size36: "max-lg:after:absolute max-lg:after:-inset-1 max-lg:after:rounded-xl max-lg:after:content-['']",
};

/** 常规文档流控件:20px 控件扩展到 44px 热区,其余类推 */
export const touchHitArea = {
  size20: `max-lg:relative ${baseAfter.size20}`,
  size24: `max-lg:relative ${baseAfter.size24}`,
  size28: `max-lg:relative ${baseAfter.size28}`,
  size32: `max-lg:relative ${baseAfter.size32}`,
  size36: `max-lg:relative ${baseAfter.size36}`,
} as const;

/** 已是 absolute/fixed 定位的控件:只扩展热区,不改变定位 */
export const touchHitAreaOverlay = baseAfter;
