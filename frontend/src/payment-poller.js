import { getPayment } from './api.js';

/**
 * 支付状态轮询（L2-10）。
 *
 * 一条支付单不是「发完请求就成功」的，服务端返回 processing，要反复查直到 paid。
 * 这里把它做成一个可以 start / stop 的小状态机，只负责「什么时候查、查到什么算什么」：
 *
 *  - 间隔 1 秒，最多查 10 次（README：轮询要有上限）；
 *  - 只有明确拿到 status === 'paid' 才回调 onPaid——processing 一律回调 onProcessing，
 *    界面永远停在「支付中…」，不会提前报成功；
 *  - 查询本身失败（503 故障注入、断网）算一次尝试，继续轮询；404（支付单不存在）没有
 *    继续查的意义，直接 onError 并停止；
 *  - stop() 立刻生效：清掉下一个定时器 + abort 在飞的请求（离开组件 / 页面必须调它，
 *    否则「离开页面 5 秒内不再有 GET /api/payments」这条验收过不了）。
 *
 * fetchPayment 可以注入，所以单测不需要真的发请求、也不需要 mock 全局 fetch。
 */

export const PAYMENT_POLL_INTERVAL = 1000;
export const PAYMENT_MAX_POLLS = 10;

export function createPaymentPoller({
  paymentId,
  interval = PAYMENT_POLL_INTERVAL,
  maxPolls = PAYMENT_MAX_POLLS,
  fetchPayment = getPayment,
  onProcessing,
  onPaid,
  onTimeout,
  onError,
} = {}) {
  let timer = 0;
  let controller = null;
  let polls = 0;
  let running = false;

  function stop() {
    running = false;
    clearTimeout(timer);
    timer = 0;
    if (controller) {
      controller.abort(); // 在飞的请求也一起掐掉，别让它晚一拍把结果写进界面
      controller = null;
    }
  }

  async function tick() {
    if (!running) return;
    polls += 1;

    controller = typeof AbortController === 'function' ? new AbortController() : null;
    let payment = null;
    let failure = null;
    try {
      payment = await fetchPayment(paymentId, controller ? { signal: controller.signal } : undefined);
    } catch (error) {
      failure = error;
    }
    controller = null;

    if (!running) return; // 请求在飞的时候被 stop 了：结果一律丢弃
    if (failure?.name === 'AbortError') return;

    if (failure && failure.status === 404) {
      stop();
      onError?.(failure);
      return;
    }

    if (!failure && payment?.status === 'paid') {
      stop();
      onPaid?.(payment);
      return;
    }

    // 到达上限：不再重试，直接交给调用方提示「支付状态未确认」
    if (polls >= maxPolls) {
      stop();
      onTimeout?.();
      return;
    }

    // 到这里要么是 processing，要么这次查询失败了还能重试：界面都保持「支付中…」
    onProcessing?.(payment, polls, failure);
    timer = setTimeout(tick, interval);
  }

  function start() {
    if (running) return;
    running = true;
    polls = 0;
    // 先等一个间隔再查：刚发完 POST 就立刻查，第一次必然是 processing，没意义
    timer = setTimeout(tick, interval);
  }

  return {
    start,
    stop,
    get polls() {
      return polls;
    },
    get running() {
      return running;
    },
  };
}
