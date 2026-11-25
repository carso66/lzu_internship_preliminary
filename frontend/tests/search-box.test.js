import { describe, it, expect } from 'vitest';
import { normalizeKeyword } from '../src/search-box.js';

describe('搜索关键词整理（L0-5）', () => {
  it('去掉首尾空格（不小心带上空格也能搜到）', () => {
    expect(normalizeKeyword('  耳机  ')).toBe('耳机');
  });

  it('连续空格压成一个，空串仍然是空串', () => {
    expect(normalizeKeyword('降噪   蓝牙\t耳机')).toBe('降噪 蓝牙 耳机');
    expect(normalizeKeyword('   ')).toBe('');
  });

  it('空值 / null / undefined 一律当空关键词（「清空筛选」靠这个回到初始状态）', () => {
    expect(normalizeKeyword('')).toBe('');
    expect(normalizeKeyword(null)).toBe('');
    expect(normalizeKeyword(undefined)).toBe('');
  });

  it('大小写和中英文原样保留，不做本地小写化（匹配规则交给接口）', () => {
    expect(normalizeKeyword('Pro')).toBe('Pro');
    expect(normalizeKeyword('A4 笔记本')).toBe('A4 笔记本');
  });
});
