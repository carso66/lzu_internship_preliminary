const CONTROL_CHARACTERS = /[\u0000-\u001F\u007F-\u009F\u200B-\u200D\u2060\uFEFF]/g
const HTML_BLOCK = /<(script|style)\b[^>]*>[\s\S]*?<\/\1\s*>/gi
const HTML_TAG = /<[^>]*>/g

/**
 * 生成用于展示的商品标题。
 *
 * 标题仍通过 Vue 的 {{ }} 以纯文本渲染；这里额外移除数据中误带的
 * script/style 内容、HTML 标签、零宽字符和控制字符，避免页面出现
 * “<script>alert('xss')</script> 二手教材”这类奇怪名称。
 * 不影响 id=23 这类正常长标题，长标题仍由 CSS 单行省略。
 *
 * @param {unknown} value 原始标题
 * @param {string} fallback 清理后为空时的兜底文案
 * @returns {string}
 */
export function normalizeProductTitle(value, fallback = '未命名商品') {
  if (typeof value !== 'string') return fallback

  const title = value
    .replace(HTML_BLOCK, ' ')
    .replace(HTML_TAG, ' ')
    .replace(CONTROL_CHARACTERS, '')
    .replace(/\s+/g, ' ')
    .trim()

  return title || fallback
}