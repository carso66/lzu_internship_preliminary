import { describe, it, expect } from 'vitest';
import { orderStatusMeta, orderStatusText } from '../src/order-status.js';

describe('订单状态文案（L1-7 要求中文）', () => {
  it('created -> 待支付，paid -> 已支付', () => {
    expect(orderStatusText('created')).toBe('待支付');
    expect(orderStatusText('paid')).toBe('已支付');
  });

  it('界面上不会出现英文枚举原文', () => {
    const texts = ['created', 'paid'].map(orderStatusText);
    expect(texts).not.toContain('created');
    expect(texts).not.toContain('paid');
    for (const text of texts) expect(text).toMatch(/[\u4e00-\u9fa5]/);
  });

  it('未知状态有兜底文案，不渲染空白徽标', () => {
    expect(orderStatusText('refunded')).toBe('未知状态');
    expect(orderStatusText(undefined)).toBe('未知状态');
  });

  it('两种状态的徽标样式不同（肉眼能区分）', () => {
    expect(orderStatusMeta('created').className).toBe('badge badge-warn');
    expect(orderStatusMeta('paid').className).toBe('badge badge-on');
    expect(orderStatusMeta('created').className).not.toBe(orderStatusMeta('paid').className);
  });
});
