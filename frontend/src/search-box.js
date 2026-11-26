import { el } from './dom.js';

/**
 * 关键词搜索框（L0-5）：挂在页面右上角「+ 发布商品」旁边。
 *
 * 三个入口都收敛到同一个 onSearch(keyword)：
 *  1. 输入时防抖（默认 300ms）——边打字边出结果，又不会一个字发一次请求；
 *  2. 回车 / 点左边的 🔍——立即搜，不等防抖；
 *  3. 点 × 清空——把关键词清成空串，列表回到初始状态。
 *
 * 去重放在这里：emit() 记住「上一次真正发出去的关键词」，
 * 同一个词不会因为「输入 耳 -> 耳机 -> 删成 耳」而重复请求。
 * 真正决定要不要发请求的是 main.js（onSearch 里比对 state.keyword）。
 */

/** 把输入框里的东西整理成要提交的关键词：去首尾空格、连续空格压成一个 */
export function normalizeKeyword(raw) {
  return String(raw ?? '')
    .trim()
    .replace(/\s+/g, ' ');
}

/**
 * @param {(keyword: string) => void} onSearch 关键词变化时调用（已经 normalize 过，可能为空串）
 * @param {number} delay 输入防抖的毫秒数
 * @returns {{ form: HTMLFormElement, input: HTMLInputElement, setValue: (v: string) => void, getValue: () => string, focus: () => void }}
 */
export function createSearchBox({ onSearch, delay = 300 } = {}) {
  let timer = 0;
  let last = ''; // 上一次真正发出去的关键词，用来去重

  const input = el('input', {
    id: 'product-search',
    type: 'search',
    className: 'search-input',
    placeholder: '搜索商品标题，如：耳机',
    autocomplete: 'off',
    maxLength: 60,
  });

  const clearBtn = el('button', {
    type: 'button',
    className: 'search-clear',
    textContent: '×',
    title: '清空关键词',
    hidden: true,
    on: { click: () => { setValue(''); emit(''); } },
  });
  // el() 只设属性/属性值，aria-* 这类没有对应 DOM 属性的走 setAttribute
  clearBtn.setAttribute('aria-label', '清空关键词');

  // 左边的 🔍 就是 submit 按钮：点它和按回车走同一条路（handleSubmit）
  const searchBtn = el('button', {
    type: 'submit',
    className: 'search-icon',
    textContent: '🔍',
    title: '搜索',
  });
  searchBtn.setAttribute('aria-label', '搜索');

  const form = el(
    'form',
    // 用 form 包起来，回车就等于提交（比手写 keydown 判断覆盖得更全：搜狗输入法选词回车也能正常处理）
    { className: 'search-box', on: { submit: handleSubmit } },
    [searchBtn, input, clearBtn],
  );
  form.setAttribute('role', 'search');

  // 输入：先切换 × 的显隐，再防抖触发搜索
  input.addEventListener('input', () => {
    toggleClear();
    clearTimeout(timer);
    timer = setTimeout(() => emit(input.value), delay);
  });

  function handleSubmit(event) {
    event?.preventDefault?.(); // 别让表单把页面提交走（Vite 下会直接刷新）
    clearTimeout(timer);
    // 回车 / 点 🔍 是明确的「再搜一次」：关键词没变也照发（比如上一次请求失败了想重试）
    emit(input.value, { force: true });
  }

  function emit(raw, { force = false } = {}) {
    const keyword = normalizeKeyword(raw);
    // 只在真的需要整理时才写回 value：中文输入法正在组词时改写 input.value 会打断组词
    if (input.value !== keyword) input.value = keyword;
    toggleClear();
    if (!force && keyword === last) return;
    last = keyword;
    onSearch?.(keyword);
  }

  function toggleClear() {
    clearBtn.hidden = normalizeKeyword(input.value) === '';
  }

  /** 外部（比如空态里的「清空筛选」）改关键词用：同步 input 和去重记录，但不触发搜索 */
  function setValue(value) {
    input.value = value ?? '';
    last = normalizeKeyword(input.value);
    toggleClear();
  }

  return {
    form,
    input,
    setValue,
    getValue: () => normalizeKeyword(input.value),
    focus: () => input.focus?.(),
  };
}
