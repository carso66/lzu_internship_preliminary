import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createPaymentPoller,
  PAYMENT_MAX_POLLS,
  PAYMENT_POLL_INTERVAL,
} from '../src/payment-poller.js';

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('支付轮询的节奏（L2-10）', () => {
  it('默认 1 秒一次、最多 10 次', () => {
    expect(PAYMENT_POLL_INTERVAL).toBe(1000);
    expect(PAYMENT_MAX_POLLS).toBe(10);
  });

  it('先等一个间隔再查，不是发完 POST 立刻查', async () => {
    const fetchPayment = vi.fn(async () => ({ status: 'processing' }));
    const poller = createPaymentPoller({ paymentId: 'PAY0', fetchPayment });

    poller.start();
    expect(fetchPayment).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(PAYMENT_POLL_INTERVAL);
    expect(fetchPayment).toHaveBeenCalledTimes(1);
    poller.stop();
  });
});

describe('processing 不能提前报成功（L2-10 的关键一条）', () => {
  it('连续两次 processing 都只回调 onProcessing，第三次 paid 才回调 onPaid', async () => {
    const statuses = ['processing', 'processing', 'paid'];
    let call = 0;
    const fetchPayment = vi.fn(async () => ({ paymentId: 'PAY1', status: statuses[call++] }));
    const onProcessing = vi.fn();
    const onPaid = vi.fn();
    const onTimeout = vi.fn();

    const poller = createPaymentPoller({
      paymentId: 'PAY1',
      fetchPayment,
      onProcessing,
      onPaid,
      onTimeout,
    });
    poller.start();

    await vi.advanceTimersByTimeAsync(1000);
    expect(fetchPayment).toHaveBeenCalledTimes(1);
    expect(onProcessing).toHaveBeenCalledTimes(1);
    expect(onPaid).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(1000);
    expect(fetchPayment).toHaveBeenCalledTimes(2);
    expect(onPaid).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(1000);
    expect(fetchPayment).toHaveBeenCalledTimes(3);
    expect(onPaid).toHaveBeenCalledTimes(1);
    expect(onPaid.mock.calls[0][0]).toMatchObject({ status: 'paid' });
    expect(onTimeout).not.toHaveBeenCalled();

    // paid 之后不再发请求（定时器已经清掉了）
    await vi.advanceTimersByTimeAsync(5000);
    expect(fetchPayment).toHaveBeenCalledTimes(3);
    expect(poller.running).toBe(false);
  });
});

describe('轮询上限（L2-10）', () => {
  it('一直 processing 时到上限就停，并提示超时', async () => {
    const fetchPayment = vi.fn(async () => ({ paymentId: 'PAY2', status: 'processing' }));
    const onPaid = vi.fn();
    const onTimeout = vi.fn();

    const poller = createPaymentPoller({ paymentId: 'PAY2', fetchPayment, onTimeout, onPaid });
    poller.start();

    await vi.advanceTimersByTimeAsync(PAYMENT_POLL_INTERVAL * PAYMENT_MAX_POLLS);
    expect(fetchPayment).toHaveBeenCalledTimes(PAYMENT_MAX_POLLS);
    expect(onTimeout).toHaveBeenCalledTimes(1);
    expect(onPaid).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(PAYMENT_POLL_INTERVAL * 5);
    expect(fetchPayment).toHaveBeenCalledTimes(PAYMENT_MAX_POLLS); // 已经停了，不会越查越多
    expect(poller.running).toBe(false);
  });
});

describe('离开组件要能立刻停（L2-10）', () => {
  it('stop() 之后不再有新的 GET', async () => {
    const fetchPayment = vi.fn(async () => ({ paymentId: 'PAY3', status: 'processing' }));
    const poller = createPaymentPoller({ paymentId: 'PAY3', fetchPayment });
    poller.start();

    await vi.advanceTimersByTimeAsync(1000);
    expect(fetchPayment).toHaveBeenCalledTimes(1);

    poller.stop();
    await vi.advanceTimersByTimeAsync(5000);
    expect(fetchPayment).toHaveBeenCalledTimes(1);
    expect(poller.running).toBe(false);
  });

  it('请求在飞的时候 stop，晚到的结果不会写进界面', async () => {
    let resolveFetch = () => {};
    const fetchPayment = vi.fn(
      () =>
        new Promise((resolve) => {
          resolveFetch = resolve;
        }),
    );
    const onProcessing = vi.fn();
    const onPaid = vi.fn();

    const poller = createPaymentPoller({ paymentId: 'PAY6', fetchPayment, onProcessing, onPaid });
    poller.start();
    await vi.advanceTimersByTimeAsync(1000);
    expect(fetchPayment).toHaveBeenCalledTimes(1);

    poller.stop();
    resolveFetch({ status: 'paid' });
    await vi.advanceTimersByTimeAsync(1000);

    expect(onPaid).not.toHaveBeenCalled();
    expect(onProcessing).not.toHaveBeenCalled();
  });
});

describe('查询失败的两种处理', () => {
  it('503 这类临时失败算一次尝试，继续轮询，到上限才报超时', async () => {
    const fetchPayment = vi.fn(async () => {
      const error = new Error('服务暂时不可用');
      error.status = 503;
      throw error;
    });
    const onTimeout = vi.fn();
    const onProcessing = vi.fn();

    const poller = createPaymentPoller({
      paymentId: 'PAY4',
      fetchPayment,
      maxPolls: 3,
      onTimeout,
      onProcessing,
    });
    poller.start();

    await vi.advanceTimersByTimeAsync(3000);
    expect(fetchPayment).toHaveBeenCalledTimes(3);
    expect(onTimeout).toHaveBeenCalledTimes(1);
    expect(onProcessing).toHaveBeenCalledTimes(2); // 前两次还在重试
  });

  it('404（支付单不存在）直接停下并报错，不做无意义的轮询', async () => {
    const fetchPayment = vi.fn(async () => {
      const error = new Error('支付单不存在');
      error.status = 404;
      throw error;
    });
    const onError = vi.fn();
    const onTimeout = vi.fn();

    const poller = createPaymentPoller({ paymentId: 'PAY5', fetchPayment, onError, onTimeout });
    poller.start();

    await vi.advanceTimersByTimeAsync(1000);
    expect(onError).toHaveBeenCalledTimes(1);
    expect(onError.mock.calls[0][0].message).toBe('支付单不存在');

    await vi.advanceTimersByTimeAsync(5000);
    expect(fetchPayment).toHaveBeenCalledTimes(1);
    expect(onTimeout).not.toHaveBeenCalled();
  });
});
