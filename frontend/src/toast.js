import { el } from './dom.js';

/**
 * 顶部提示条：发布成功 / 下单成功 / 上下架 / 请求失败都用它。
 *
 * 抽成独立模块的原因：现在有两个面板（发布、购买）和列表操作都要提示，
 * 如果每个模块自己建一条 toast，就会在页面同一位置叠起来。
 * 所以由 main.js 建一个实例、挂到 body，再把 show 传给需要的地方。
 *
 * 4.5 秒后自动消失；新的提示会顶掉旧的（清掉上一个定时器）。
 */
export function createToast({ duration = 4500 } = {}) {
  const node = el('div', { className: 'toast', hidden: true });
  let timer = 0;

  function hide() {
    clearTimeout(timer);
    node.hidden = true;
  }

  /** show('发布成功') / show('库存不足', 'error') */
  function show(text, type = 'success') {
    clearTimeout(timer);
    node.textContent = text;
    node.className = type === 'error' ? 'toast is-error' : 'toast';
    node.hidden = false;
    timer = setTimeout(() => {
      node.hidden = true;
    }, duration);
  }

  return { node, show, hide };
}
