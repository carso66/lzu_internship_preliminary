import { describe, it, expect } from 'vitest';
import { CATEGORIES, validateProductForm } from '../src/publish-form.js';

const valid = {
  title: '考研数学复习全书',
  price: '19.9',
  stock: '5',
  category: '图书',
  description: '',
};

describe('发布表单的字段（L0-2）', () => {
  it('分类恰好 5 项，且与接口约定一致', () => {
    expect(CATEGORIES).toEqual(['数码', '图书', '服饰', '食品', '文具']);
  });

  it('合法输入没有错误', () => {
    expect(validateProductForm(valid)).toEqual({});
  });
});

describe('校验：标题', () => {
  it('必填、去空格后不能为空', () => {
    expect(validateProductForm({ ...valid, title: '' }).title).toBe('标题不能为空');
    expect(validateProductForm({ ...valid, title: '    ' }).title).toBe('标题不能为空');
  });

  it('最多 50 字', () => {
    expect(validateProductForm({ ...valid, title: 'a'.repeat(50) }).title).toBeUndefined();
    expect(validateProductForm({ ...valid, title: 'a'.repeat(51) }).title).toBe('标题最多 50 个字');
  });
});

describe('校验：价格（元）', () => {
  it('不同量级的合法值都能过', () => {
    for (const price of ['12', '19.9', '12.50', '0.01', '.5', '12.']) {
      expect(validateProductForm({ ...valid, price }).price).toBeUndefined();
    }
  });

  it('必填 / 必须大于 0 / 最多两位小数', () => {
    expect(validateProductForm({ ...valid, price: '' }).price).toBe('价格不能为空');
    expect(validateProductForm({ ...valid, price: '0' }).price).toBe('价格必须大于 0');
    expect(validateProductForm({ ...valid, price: '0.00' }).price).toBe('价格必须大于 0');
    expect(validateProductForm({ ...valid, price: '19.999' }).price).toMatch(/最多两位小数/);
    expect(validateProductForm({ ...valid, price: 'abc' }).price).toMatch(/金额格式不正确/);
  });
});

describe('校验：库存', () => {
  it('边界值 0 / 999 通过', () => {
    expect(validateProductForm({ ...valid, stock: '0' }).stock).toBeUndefined();
    expect(validateProductForm({ ...valid, stock: '999' }).stock).toBeUndefined();
  });

  it('必填 / 负数 / 小数 / 超过 999 都被拦下', () => {
    expect(validateProductForm({ ...valid, stock: '' }).stock).toBe('库存不能为空');
    expect(validateProductForm({ ...valid, stock: '-1' }).stock).toBe('库存必须是 0~999 的整数');
    expect(validateProductForm({ ...valid, stock: '1.5' }).stock).toBe('库存必须是 0~999 的整数');
    expect(validateProductForm({ ...valid, stock: '1000' }).stock).toBe('库存必须是 0~999 的整数');
  });
});

describe('校验：分类 / 描述', () => {
  it('分类必须是下拉里的值', () => {
    expect(validateProductForm({ ...valid, category: '' }).category).toBe('请选择分类');
    expect(validateProductForm({ ...valid, category: '玩具' }).category).toBe('请选择分类');
  });

  it('描述可以不填，但最多 200 字', () => {
    expect(validateProductForm({ ...valid, description: 'a'.repeat(200) }).description).toBeUndefined();
    expect(validateProductForm({ ...valid, description: 'a'.repeat(201) }).description).toBe(
      '描述最多 200 个字',
    );
  });
});
