import { createPayment } from './api.js';
import { el } from './dom.js';
import { formatCnySafe } from './money.js';
import { createPaymentPoller, PAYMENT_MAX_POLLS } from './payment-poller.js';
import { displayTitle } from './text.js';

/**
 * 支付面板（L2-10）。
 *
 * 流程：订单列表点「去支付」→ 选支付宝 / 微信 → POST /api/payments
 *   → 按钮变「支付中…」并 disabled → 每秒 GET /api/payments/{paymentId}
 *   → processing 期间界面一直是「支付中…」→ paid 后才提示成功
 *   → 关闭弹窗 / 离开页面立刻停止轮询（poller.stop + abort）。
 *
 * 三个外部回调都由 main.js 提供：面板只管「发起 + 轮询 + 这个弹窗里的显示」，
 * 订单列表的数据和按钮状态由调用方维护。
 *  - onStart(order)：支付单创建成功，这一行进入「支付中…」锁定；
 *  - onSettled(order, payment)：轮询到 paid，行按钮永久禁用 + 刷新订单；
 *  - onAbort(order)：轮询结束但没确认成功（超时 / 查询失败 / 用户关掉弹窗），解锁这一行。
 */

export const PAYMENT_METHODS = [
  { value: 'alipay', label: '支付宝' },
  { value: 'wechat', label: '微信' },
];

export function createPaymentPanel({ notify, onStart, onSettled, onAbort } = {}) {
  let order = null;
  let phase = 'idle'; // 'idle' 可再次发起 / 'paying' 正在等结果
  let poller = null;

  const orderNoEl = el('p', { className: 'pay-order-no' });
  const productEl = el('p', { className: 'pay-product' });
  const amountEl = el('strong', { className: 'pay-amount' });
  const statusEl = el('p', { className: 'pay-status' });
  const messageEl = el('p', { className: 'form-message' });

  const radios = PAYMENT_METHODS.map(({ value, label }) => {
    const input = el('input', {
      id: `pay-method-${value}`,
      type: 'radio',
      name: 'pay-method',
      value,
      checked: value === PAYMENT_METHODS[0].value,
    });
    const node = el('label', { className: 'pay-method', htmlFor: input.id }, [
      input,
      el('span', { textContent: label }),
    ]);
    return { input, node };
  });

  const actionBtn = el('button', { type: 'submit', className: 'btn-primary', textContent: '确认支付' });
  const cancelBtn = el('button', {
    type: 'button',
    className: 'btn-plain',
    textContent: '取消',
    on: { click: close },
  });

  const form = el(
    'form',
    { className: 'pay-form', noValidate: true, on: { submit: handleSubmit } },
    [
      orderNoEl,
      productEl,
      el('p', { className: 'pay-amount-line' }, [el('span', { textContent: '应付金额：' }), amountEl]),
      el('div', { className: 'field' }, [
        el('span', { className: 'field-label', textContent: '支付方式' }),
        el('div', { className: 'pay-methods' }, radios.map(({ node }) => node)),
      ]),
      statusEl,
      el('div', { className: 'form-actions' }, [actionBtn, cancelBtn]),
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
        el('h2', { className: 'modal-title', textContent: '支付订单' }),
        closeBtn,
      ]),
      form,
    ]),
  ]);
  // 点遮罩关 = 离开这个组件：poller 会被一起停掉
  mask.addEventListener('click', (event) => {
    if (event.target === mask) close();
  });

  if (typeof document.addEventListener === 'function') {
    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && !mask.hidden) close();
    });
  }
  // 关标签页 / 刷新 / 跳走：浏览器不会再给我们机会，先停轮询
  if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
    window.addEventListener('pagehide', () => {
      if (phase === 'paying') close();
    });
  }

  function selectedMethod() {
    const checked = radios.find(({ input }) => input.checked);
    return checked ? checked.input.value : PAYMENT_METHODS[0].value;
  }

  function setBusy(busy, label) {
    phase = busy ? 'paying' : 'idle';
    actionBtn.disabled = busy;
    actionBtn.textContent = label ?? (busy ? '支付中…' : '确认支付');
    actionBtn.className = busy ? 'btn-primary is-busy' : 'btn-primary';
    for (const { input } of radios) input.disabled = busy;
  }

  function setStatus(text) {
    statusEl.textContent = text;
  }

  function setMessage(text, type = '') {
    messageEl.textContent = text;
    messageEl.className = ['form-message', text ? 'is-visible' : '', type ? `is-${type}` : '']
      .filter(Boolean)
      .join(' ');
  }

  function stopPolling() {
    if (!poller) return;
    poller.stop();
    poller = null;
  }

  function open(next) {
    if (!next) return;
    // 遮罩本来就会挡住其它行；这里再兜一层：上一次还在轮询就先停掉并解锁那一行，
    // 免得两个轮询同时跑、晚到的结果把别的订单写脏
    if (phase === 'paying') close();
    order = next;
    setBusy(false);
    orderNoEl.textContent = `订单号：${next.orderNo ?? `#${next.id}`}`;
    productEl.textContent = displayTitle(next.productTitle);
    productEl.title = String(next.productTitle ?? '');
    amountEl.textContent = formatCnySafe(next.totalAmount);
    setStatus('');
    setMessage('');
    // 每次打开都回到默认的支付宝，别让上一次的选择残留
    for (const { input } of radios) input.checked = input.value === PAYMENT_METHODS[0].value;
    mask.hidden = false;
    actionBtn.focus?.();
  }

  function close() {
    const target = order;
    const wasPaying = phase === 'paying';
    stopPolling(); // 关掉组件就停轮询（含在飞的那个 GET）
    setBusy(false);
    setStatus('');
    mask.hidden = true;
    order = null;
    // 支付还没确认就被关掉：解锁订单行，用户可以重新点「去支付」
    if (wasPaying && target) onAbort?.(target);
  }

  function startPolling(target, paymentId) {
    poller = createPaymentPoller({
      paymentId,
      onProcessing: (payment, polls) => {
        // 只有 paid 才算成功；processing（或一次查询失败）一律保持「支付中…」
        setStatus(`支付中…（第 ${polls} 次确认，最多 ${PAYMENT_MAX_POLLS} 次）`);
      },
      onPaid: (payment) => {
        poller = null;
        const paidOrder = target;
        setBusy(false);
        setStatus('');
        setMessage('支付成功', 'success');
        notify?.(
          `支付成功：${paidOrder.orderNo ?? `#${paidOrder.id}`}，${formatCnySafe(
            payment?.amount ?? paidOrder.totalAmount,
          )}`,
        );
        onSettled?.(paidOrder, payment);
        close(); // 此时 phase 已是 idle，close 不会再触发 onAbort
      },
      onTimeout: () => {
        poller = null;
        setBusy(false, '重新确认');
        setStatus('');
        setMessage('支付状态未确认，请稍后在订单列表查看', 'error');
        onAbort?.(target);
      },
      onError: (error) => {
        poller = null;
        setBusy(false, '重新确认');
        setStatus('');
        setMessage(error?.message || '支付状态查询失败，请稍后重试', 'error');
        onAbort?.(target);
      },
    });
    poller.start();
  }

  async function handleSubmit(event) {
    event?.preventDefault?.();
    if (phase === 'paying' || !order) return; // 支付中连点直接忽略

    const target = order;
    setMessage('');
    setBusy(true, '发起支付…');
    setStatus('正在创建支付单…');
    try {
      const payment = await createPayment({ orderId: target.id, method: selectedMethod() });
      // 请求期间被关掉了：既不锁定订单行，也不要开始轮询
      if (!order || order.id !== target.id) return;
      if (!payment?.paymentId) throw new Error('支付单创建失败，请稍后重试');

      onStart?.(target);
      setBusy(true, '支付中…');
      setStatus('支付中…');
      startPolling(target, payment.paymentId);
    } catch (error) {
      setBusy(false);
      setStatus('');
      // 业务错误原样显示：例如对已支付订单发起支付会拿到「订单已支付，请勿重复支付」
      setMessage(error?.message || '支付失败，请稍后重试', 'error');
    }
  }

  return { mask, open, close };
}
