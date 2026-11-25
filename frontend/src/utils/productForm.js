export const PRODUCT_CATEGORIES = ['数码', '图书', '服饰', '食品', '文具']

/**
 * 按码点计算字符串长度，避免 emoji 等代理对字符被拆成两个长度。
 *
 * @param {unknown} value
 * @returns {number}
 */
export function countCharacters(value) {
  return Array.from(String(value ?? '')).length
}

function stringField(value) {
  return typeof value === 'string' ? value.trim() : ''
}

/**
 * 校验商品发布表单。
 *
 * @param {object} form 表单字段，允许缺失或非字符串以便测试边界输入。
 * @returns {{ valid: boolean, errors: Record<string, string> }}
 */
export function validateProductForm(form = {}) {
  const errors = {
    title: '',
    price: '',
    stock: '',
    category: '',
    description: '',
  }

  const title = stringField(form.title)
  const titleLength = countCharacters(title)
  if (titleLength === 0) {
    errors.title = '标题不能为空'
  } else if (titleLength > 50) {
    errors.title = '标题不能超过 50 个字'
  }

  const price = stringField(form.price)
  if (!price) {
    errors.price = '价格不能为空'
  } else if (!/^[0-9]+(?:\.[0-9]{1,2})?$/.test(price)) {
    errors.price = '价格必须是大于 0 的数字，最多两位小数'
  } else if (!(Number(price) > 0)) {
    errors.price = '价格必须大于 0'
  }

  const stock = stringField(form.stock)
  if (!stock) {
    errors.stock = '库存不能为空'
  } else if (!/^[0-9]+$/.test(stock) || Number(stock) > 999) {
    errors.stock = '库存必须是 0~999 的整数'
  }

  if (!PRODUCT_CATEGORIES.includes(form.category)) {
    errors.category = '请选择有效的分类'
  }

  const descriptionLength = countCharacters(stringField(form.description))
  if (descriptionLength > 200) {
    errors.description = '描述不能超过 200 字'
  }

  return {
    valid: !Object.values(errors).some(Boolean),
    errors,
  }
}
