/**
 * 极简 DOM 构建工具。
 * 统一用 createElement + textContent，不拼 innerHTML：
 * mock 的 db.json 里商品 24 的标题就是 <script>alert('xss')</script>，
 * 用字符串拼接就等于把注入入口留给数据。
 */
export function el(tag, props = {}, children = []) {
  const node = document.createElement(tag);
  Object.assign(node, props);
  for (const child of children) {
    node.append(child);
  }
  return node;
}
