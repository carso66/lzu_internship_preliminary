import { describe, expect, it } from 'vitest'
import {
  countCharacters,
  PRODUCT_CATEGORIES,
  validateProductForm,
} from './productForm.js'

const validForm = {
  title: '九成新英语听力耳机',
  price: '19.9',
  stock: '10',
  category: '数码',
  description: '原装收纳盒齐全',
}

describe('countCharacters', () => {
  it('按码点计数，emoji 不会被拆成两个长度', () => {
    expect(countCharacters('😀')).toBe(1)
    expect(countCharacters('a😀b')).toBe(3)
  })

  it('空值和非法类型安全返回 0', () => {
    expect(countCharacters('')).toBe(0)
    expect(countCharacters(null)).toBe(0)
    expect(countCharacters(undefined)).toBe(0)
  })
})

describe('validateProductForm', () => {
  it('接受完整合法表单', () => {
    const result = validateProductForm(validForm)

    expect(result.valid).toBe(true)
    expect(result.errors).toEqual({
      title: '',
      price: '',
      stock: '',
      category: '',
      description: '',
    })
  })

  it('标题空、1 字和 50 字通过，51 字报错', () => {
    expect(validateProductForm({ ...validForm, title: ' ' }).errors.title).toBe(
      '标题不能为空',
    )
    expect(
      validateProductForm({ ...validForm, title: '书' }).errors.title,
    ).toBe('')
    expect(
      validateProductForm({ ...validForm, title: '商'.repeat(50) }).errors
        .title,
    ).toBe('')
    expect(
      validateProductForm({ ...validForm, title: '商'.repeat(51) }).errors
        .title,
    ).toBe('标题不能超过 50 个字')
  })

  it('标题字段缺失或非字符串按空标题处理', () => {
    expect(
      validateProductForm({ ...validForm, title: undefined }).errors.title,
    ).toBe('标题不能为空')
    expect(validateProductForm({ ...validForm, title: 123 }).errors.title).toBe(
      '标题不能为空',
    )
  })

  it('价格边界与非法格式', () => {
    expect(
      validateProductForm({ ...validForm, price: '0.01' }).errors.price,
    ).toBe('')
    expect(validateProductForm({ ...validForm, price: '0' }).errors.price).toBe(
      '价格必须大于 0',
    )
    expect(validateProductForm({ ...validForm, price: '' }).errors.price).toBe(
      '价格不能为空',
    )
    expect(
      validateProductForm({ ...validForm, price: '19.999' }).errors.price,
    ).toBe('价格必须是大于 0 的数字，最多两位小数')
    expect(
      validateProductForm({ ...validForm, price: '-1' }).errors.price,
    ).toBe('价格必须是大于 0 的数字，最多两位小数')
    expect(
      validateProductForm({ ...validForm, price: '1e3' }).errors.price,
    ).toBe('价格必须是大于 0 的数字，最多两位小数')
  })

  it('库存 0 和 999 通过，1000、负数和非法字符串报错', () => {
    expect(validateProductForm({ ...validForm, stock: '0' }).errors.stock).toBe(
      '',
    )
    expect(
      validateProductForm({ ...validForm, stock: '999' }).errors.stock,
    ).toBe('')
    expect(
      validateProductForm({ ...validForm, stock: '1000' }).errors.stock,
    ).toBe('库存必须是 0~999 的整数')
    expect(
      validateProductForm({ ...validForm, stock: '-1' }).errors.stock,
    ).toBe('库存必须是 0~999 的整数')
    expect(
      validateProductForm({ ...validForm, stock: '1.5' }).errors.stock,
    ).toBe('库存必须是 0~999 的整数')
    expect(validateProductForm({ ...validForm, stock: '' }).errors.stock).toBe(
      '库存不能为空',
    )
  })

  it('分类只能是预置五项', () => {
    expect(PRODUCT_CATEGORIES).toEqual(['数码', '图书', '服饰', '食品', '文具'])
    expect(
      validateProductForm({ ...validForm, category: '图书' }).errors.category,
    ).toBe('')
    expect(
      validateProductForm({ ...validForm, category: '其他' }).errors.category,
    ).toBe('请选择有效的分类')
  })

  it('描述 200 字通过，201 字报错，emoji 按单个字符计算', () => {
    expect(
      validateProductForm({ ...validForm, description: '描'.repeat(200) })
        .errors.description,
    ).toBe('')
    expect(
      validateProductForm({ ...validForm, description: '描'.repeat(201) })
        .errors.description,
    ).toBe('描述不能超过 200 字')
    expect(
      validateProductForm({ ...validForm, description: '😀'.repeat(200) })
        .errors.description,
    ).toBe('')
    expect(
      validateProductForm({ ...validForm, description: `${'😀'.repeat(200)}x` })
        .errors.description,
    ).toBe('描述不能超过 200 字')
  })

  it('整体缺失时返回所有必填项错误', () => {
    const result = validateProductForm()

    expect(result.valid).toBe(false)
    expect(result.errors).toEqual({
      title: '标题不能为空',
      price: '价格不能为空',
      stock: '库存不能为空',
      category: '请选择有效的分类',
      description: '',
    })
  })
})
