import { describe, it, expect } from 'vitest';
import { displayTitle } from '../src/text.js';

describe('displayTitle（展示层清洗）', () => {
  it('剥掉注入探针里的标签和脚本内容，只留下正文', () => {
    expect(displayTitle("<script>alert('xss')</script> 二手教材")).toBe('二手教材');
    expect(displayTitle('<b>加粗</b>标题')).toBe('加粗标题');
    expect(displayTitle('<style>.a{color:red}</style>手冲挂耳咖啡')).toBe('手冲挂耳咖啡');
  });

  it('正常标题原样返回', () => {
    expect(displayTitle('降噪蓝牙耳机 Pro')).toBe('降噪蓝牙耳机 Pro');
    expect(displayTitle('兰州大学纪念卫衣')).toBe('兰州大学纪念卫衣');
  });

  it('去掉首尾空格并合并连续空白', () => {
    expect(displayTitle('  A4   笔记本  ')).toBe('A4 笔记本');
  });

  it('整条都是标签时退回原文，不返回空标题', () => {
    expect(displayTitle('<b></b>')).toBe('<b></b>');
    expect(displayTitle('')).toBe('');
  });
});
