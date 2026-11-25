/**
 * 极简 DOM 构建工具。
 * 统一用 createElement + textContent，不拼 innerHTML：
 * mock 的 db.json 里商品 24 的标题就是 <script>alert('xss')</script>，
 * 用字符串拼接就等于把注入入口留给数据。
 */

// 这几个属性一旦被传进来，等于把「只用 textContent」的约束拆掉，直接拒绝。
// 加它不是为了刁难，而是让「别拼字符串」这条规矩有代码兜底，而不是靠自觉。
const FORBIDDEN_PROPS = new Set(['innerHTML', 'outerHTML', 'insertAdjacentHTML']);

/**
 * el('button', {
 *   type: 'button',
 *   className: 'btn',
 *   textContent: '下架',
 *   dataset: { id: 3, action: 'off' },   // -> data-id="3" data-action="off"
 *   on: { click: handleClick },          // -> addEventListener('click', ...)
 * })
 *
 * 支持三种特殊 key：
 *  - on:      { 事件名: 处理函数 }，等价于 addEventListener（渲染后不用再 querySelector 找节点）
 *  - dataset: { key: value }，产出 data-*，事件委托靠它读行号 / 动作
 *  - 其余 key 直接 set 到节点属性上（textContent / className / value / disabled …）
 * undefined / null / false 的属性会跳过，所以 `disabled: false` 不会写出 disabled="false"
 */
export function el(tag, props = {}, children = []) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value === undefined || value === null || value === false) continue;

    if (FORBIDDEN_PROPS.has(key)) {
      throw new Error(`el(): 不允许设置 ${key}，请改用 textContent / append() 组装节点`);
    }
    if (key === 'on') {
      for (const [type, handler] of Object.entries(value)) {
        if (typeof handler === 'function') node.addEventListener(type, handler);
      }
      continue;
    }
    if (key === 'dataset') {
      Object.assign(node.dataset, value);
      continue;
    }
    node[key] = value;
  }
  appendChildren(node, children);
  return node;
}

/** 批量 append，自动跳过 null / undefined / false（`cond && el(...)` 这种写法可以直接传） */
export function appendChildren(node, children = []) {
  for (const child of children) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child);
  }
  return node;
}

/** 把一组节点打包成 DocumentFragment：整块视图一次 replaceChildren 换掉 */
export function fragment(children = []) {
  const node = document.createDocumentFragment();
  appendChildren(node, children);
  return node;
}
