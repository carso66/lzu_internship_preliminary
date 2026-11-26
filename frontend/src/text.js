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
 * 取舍：它只认「完整 script/style 块」和「白名单小写标签」，
 * 因此没闭合的 <script>alert(1) 会把 alert(1) 当正文留下（这是有意的：
 * 宁可少删一点，也不要把 `价格 <50 元>` 这类正文删掉）。
 */
// 先删「完整的 script / style 块」（含内容），再剥普通标签。
// 第二步是白名单而不是「任意 <...>」：早先用 /<[^>]*>/g 会把正文里的尖括号一起吃掉，
// 例如「价格 <50 元> 转让」-> 「价格 转让」、「A<B>C」-> 「AC」。
// 现在只认「小写标签名 + 在已知标签表里」，正文里的大写 / 数字开头的尖括号原样保留。
const KNOWN_TAGS = [
  'a', 'abbr', 'article', 'aside', 'audio', 'b', 'big', 'blockquote', 'br', 'button', 'canvas',
  'center', 'code', 'dd', 'del', 'details', 'div', 'dl', 'dt', 'em', 'embed', 'figcaption',
  'figure', 'font', 'footer', 'form', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'header', 'hr', 'i',
  'iframe', 'img', 'input', 'ins', 'kbd', 'label', 'li', 'main', 'mark', 'nav', 'noscript',
  'object', 'ol', 'p', 'picture', 'pre', 'q', 's', 'section', 'select', 'small', 'source', 'span',
  'strike', 'strong', 'sub', 'summary', 'sup', 'svg', 'table', 'tbody', 'td', 'textarea', 'tfoot',
  'th', 'thead', 'tr', 'tt', 'u', 'ul', 'video',
].join('|');

// 注意这里是大小写敏感的 'g'：<B> 不当标签看，<b> 才当
const TAG_PATTERN = new RegExp(`</?(?:${KNOWN_TAGS})\\b[^<>]*>`, 'g');
const SCRIPT_STYLE_BLOCK_PATTERN = /<(script|style)\b[^<>]*>[\s\S]*?<\/\1\s*>/gi;

/**
 * displayTitle('<script>alert(1)</script> 二手教材') === '二手教材'
 * displayTitle('<b>加粗</b>标题') === '加粗标题'
 * displayTitle('降噪蓝牙耳机 Pro') === '降噪蓝牙耳机 Pro'
 * displayTitle('价格 <50 元> 转让') === '价格 <50 元> 转让'   // 正文尖括号不误伤
 */
export function displayTitle(raw) {
  const text = String(raw ?? '');
  const cleaned = text
    .replace(SCRIPT_STYLE_BLOCK_PATTERN, '')
    .replace(TAG_PATTERN, '')
    .replace(/\s+/g, ' ')
    .trim();
  // 整条都是标签（如 '<b></b>'）时清完是空串，此时退回原文，
  // 避免列表里出现"有行没标题"这种更糟的表现
  return cleaned.length > 0 ? cleaned : text.trim();
}
