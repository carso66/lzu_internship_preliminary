import { describe, it, expect } from 'vitest';
import { actionLabel, statusMeta, statusText, toggleTarget } from '../src/product-status.js';

describe('商品状态的单一事实来源（L0-4）', () => {
  it('徽标文案与接口约定一致', () => {
    expect(statusText('on')).toBe('在售');
    expect(statusText('off')).toBe('已下架');
  });

  it('未知枚举有兜底文案，不会渲染出空白徽标', () => {
    expect(statusText('offline')).toBe('未知状态');
    expect(statusText(undefined)).toBe('未知状态');
    expect(statusMeta('banned').className).toContain('badge');
  });

  it('按钮文案与状态相反：在售 -> 下架，已下架 -> 上架', () => {
    expect(actionLabel('on')).toBe('下架');
    expect(actionLabel('off')).toBe('上架');
  });

  it('目标状态正好是按钮文案对应的那个值（请求体里的 status）', () => {
    expect(toggleTarget('on')).toBe('off');
    expect(toggleTarget('off')).toBe('on');
  });

  it('未知状态不提供操作，避免发出后端必拒的请求', () => {
    expect(toggleTarget('offline')).toBe('');
    expect(actionLabel('offline')).toBe('');
  });

  it('反复切换两次会回到原状态（按钮文案和徽标不会打架）', () => {
    const first = toggleTarget('on');
    const second = toggleTarget(first);
    expect(second).toBe('on');
    expect(actionLabel(second)).toBe('下架');
    expect(statusText(second)).toBe('在售');
  });
});
