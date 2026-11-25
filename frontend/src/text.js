/**
 * 展示层文案清洗：只决定"界面上显示什么"，不改变接口数据、不改变提交内容。
 *
 * 为什么需要它：mock 的 db.json 里商品 24 的标题是
 *   <script>alert('xss')</script> 二手教材
 * 这是题目故意埋的注入探针。我们渲染时用 textContent（不会执行脚本），
 * 所以安全上没问题；但把标签原样摊在表格里纯粹是视觉噪音，
 * 因此"显示"这一层把标签剥掉，数据仍然原封不动。
 *
 * 注意：这份清洗只做显示，不做安全兜底——安全的兜底永远是 textContent，
 * 即使清洗函数漏掉了什么，标签也只会被当成文字显示，不会被执行。
 */
const TAG_PATTERN = /<[^>]*>/g;
// 标签订正只删标签、会留下标签里的文本（<script>alert(1)</script> -> alert(1)），
// 所以先把 script / style 整块（含内容）删掉，再剥普通标签
const DANGEROUS_BLOCK_PATTERN = /<(script|style)\b[^>]*>[\s\S]*?<\/\1\s*>/gi;

/**
 * displayTitle('<script>alert(1)</script> 二手教材') === '二手教材'
 * displayTitle('<b>加粗</b>标题') === '加粗标题'
 * displayTitle('降噪蓝牙耳机 Pro') === '降噪蓝牙耳机 Pro'
 */
export function displayTitle(raw) {
  const text = String(raw ?? '');
  const cleaned = text
    .replace(DANGEROUS_BLOCK_PATTERN, '')
    .replace(TAG_PATTERN, '')
    .replace(/\s+/g, ' ')
    .trim();
  // 整条都是标签（如 '<b></b>'）时清完是空串，此时退回原文，
  // 避免列表里出现"有行没标题"这种更糟的表现
  return cleaned.length > 0 ? cleaned : text.trim();
}
