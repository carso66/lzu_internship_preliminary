import { createOrder } from './api.js';
import { el } from './dom.js';
import { formatCny, formatCnySafe, multiplyCents } from './money.js';
import { displayTitle } from './text.js';

/**
 * 购买面板（L1-6 + L1-8 的金额预估）。
 *
 * 流程：列表点「购买」→ open(product) 弹窗 → 填数量 → POST /api/orders
 *  - 数量只校验「正整数」，**不**在本地拦库存 / 上下架：
 *    README 要求这两种失败把后端的 message（「库存不足」「商品已下架」）显示出来，
 *    前端提前拦掉就等于把这两条验收项测没了。
 *  - 校验不过一个请求都不发（和发布表单同一条规矩）。
 *  - 预估总价 = 单价 × 数量，两边都是整数分，和服务端 totalAmount 完全一致。
 *  - 下单中按钮禁用 +「提交中…」，失败后恢复可点（L1-6 最后一条验收）。
 *
 * 面板不自己建 toast：提示条由 main.js 注入 notify，全页共用一条。
 */

/** 数量输入校验：通过返回 ''，不通过返回给用户看的文案 */
export function quantityInputError(input) {
  const text = String(input ?? '').trim();
  if (text === '') return '购买数量不能为空';
  if (!/^\d+$/.test(text)) return '购买数量必须是正整数';
  if (Number(text) <= 0) return '购买数量必须大于 0';
  return '';
}

/** 预估总价（分）；数量非法 / 金额异常时返回 null，界面显示「—」而不是 NaN */
export function estimateTotal(unitCents, quantity) {
  if (quantityInputError(quantity)) return null;
  try {
    return multiplyCents(unitCents, Number(String(quantity).trim()));
  } catch {
    return null;
  }
}

/**
 * @param {(order: object) => void} onOrdered 下单成功（201）后调用，由外部刷新商品库存和订单列表
 * @param {(text: string, type?: string) => void} notify 顶部提示条
 */
export function createPurchasePanel({ onOrdered, notify } = {}) {
  let product = null;
  let isOpen = false;
  let submitting = false;

  const titleEl = el('p', { className: 'purchase-title' });
  const unitEl = el('span', { className: 'purchase-unit' });
  const stockEl = el('span', { className: 'purchase-stock' });
  const qtyInput = el('input', {
    id: 'purchase-quantity',
    type: 'text',
    inputMode: 'numeric',
    autocomplete: 'off',
    value: '1',
  });
  const qtyError = el('p', { className: 'field-error' });
  const estimateEl = el('strong', { className: 'purchase-total' });
  const messageEl = el('p', { className: 'form-message' });
  const submitBtn = el('button', { type: 'submit', className: 'btn-primary', textContent: '确认购买' });
  const cancelBtn = el('button', {
    type: 'button',
    className: 'btn-plain',
    textContent: '取消',
    on: { click: close },
  });

  const form = el(
    'form',
    { className: 'purchase-form', noValidate: true, on: { submit: handleSubmit } },
    [
      titleEl,
      el('p', { className: 'purchase-meta' }, [unitEl, stockEl]),
      el('div', { className: 'field' }, [
        el('label', { className: 'field-label', htmlFor: qtyInput.id, textContent: '购买数量' }),
        qtyInput,
        qtyError,
        el('p', { className: 'field-hint', textContent: '填正整数；超过库存时后端会提示「库存不足」' }),
      ]),
      el('p', { className: 'purchase-estimate' }, [
        el('span', { textContent: '预估总价：' }),
        estimateEl,
      ]),
      el('div', { className: 'form-actions' }, [submitBtn, cancelBtn]),
      messageEl,
    ],
  );

  const closeBtn = el('button', {
    type: 'button',
    className: 'modal-close',
    textContent: '×',
    on: { click: close },
  });
  const mask = el('div', { className: 'modal-mask', hidden: true }, [
    el('div', { className: 'modal' }, [
      el('div', { className: 'modal-head' }, [
        el('h2', { className: 'modal-title', textContent: '购买商品' }),
        closeBtn,
      ]),
      form,
    ]),
  ]);
  mask.addEventListener('click', (event) => {
    if (event.target === mask) close();
  });
  if (typeof document.addEventListener === 'function') {
    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && isOpen) close();
    });
  }

  // 一改数量就重算预估总价，同时把上一次的数量错误清掉
  qtyInput.addEventListener('input', () => {
    setQuantityError('');
    updateEstimate();
  });

  function updateEstimate() {
    const total = estimateTotal(product?.price, qtyInput.value);
    estimateEl.textContent = total === null ? '—' : formatCny(total);
  }

  function setQuantityError(text) {
    qtyError.textContent = text;
    qtyInput.classList.toggle('is-invalid', Boolean(text));
  }

  function setMessage(text, type = '') {
    messageEl.textContent = text;
    messageEl.className = ['form-message', text ? 'is-visible' : '', type ? `is-${type}` : '']
      .filter(Boolean)
      .join(' ');
  }

  function setSubmitting(busy) {
    submitting = busy;
    submitBtn.disabled = busy;
    submitBtn.textContent = busy ? '提交中…' : '确认购买';
    submitBtn.className = busy ? 'btn-primary is-busy' : 'btn-primary';
  }

  /** 打开面板：把这一行商品的价格 / 库存带进来，数量从 1 开始 */
  function open(next) {
    if (!next) return;
    product = next;
    isOpen = true;
    titleEl.textContent = `#${next.id} ${displayTitle(next.title)}`;
    titleEl.title = String(next.title ?? '');
    unitEl.textContent = `单价 ${formatCnySafe(next.price)}`;
    stockEl.textContent = `库存 ${next.stock ?? '--'}`;
    qtyInput.value = '1';
    setQuantityError('');
    setMessage('');
    updateEstimate();
    mask.hidden = false;
    qtyInput.focus?.();
    qtyInput.select?.();
  }

  function close() {
    if (!isOpen) return;
    isOpen = false;
    mask.hidden = true;
  }

  async function handleSubmit(event) {
    event?.preventDefault?.();
    if (submitting || !product) return; // 提交中连点直接忽略

    const error = quantityInputError(qtyInput.value);
    setQuantityError(error);
    if (error) {
      setMessage(''); // 本地校验不过：Network 里不该出现 POST /api/orders
      return;
    }

    setMessage('');
    setSubmitting(true);
    try {
      const order = await createOrder({
        productId: product.id,
        quantity: Number(String(qtyInput.value).trim()), // 必须是数字，不是 "2"
      });
      close();
      notify?.(`下单成功：${order.orderNo}，${formatCnySafe(order.totalAmount)}（${order.quantity} 件）`);
      onOrdered?.(order);
    } catch (err) {
      // 后端 message 原样显示：库存不足 / 商品已下架 / 购买数量必须是正整数
      setMessage(err?.message || '下单失败，请稍后重试', 'error');
    } finally {
      setSubmitting(false); // 失败了也要能重试
    }
  }

  return { mask, open, close };
}
