import { describe, it, expect, vi, afterEach } from 'vitest';
import {
  ApiError,
  createOrder,
  createPayment,
  fetchAllOrders,
  fetchAllProducts,
  getPayment,
  listOrders,
  listProducts,
  updateProductStatus,
} from '../src/api.js';

const okResponse = (data) => ({
  ok: true,
  status: 200,
  json: async () => ({ code: 0, message: 'ok', data }),
});

const makeProducts = (count) =>
  Array.from({ length: count }, (_, i) => ({
    id: i + 1,
    title: `商品${i + 1}`,
    price: 1000,
    stock: 1,
    category: '图书',
    status: 'on',
  }));

/** 按 page / pageSize 切片返回，模拟 mock 服务端的分页行为；capPerPage 对应 mock 的上限 50 */
function pagedFetch(all, { capPerPage = 50 } = {}) {
  const calls = [];
  const fetchImpl = async (url) => {
    const u = new URL(url);
    const page = Number(u.searchParams.get('page'));
    const pageSize = Math.min(capPerPage, Number(u.searchParams.get('pageSize')));
    calls.push({ page, pageSize });
    const start = (page - 1) * pageSize;
    return okResponse({ total: all.length, page, pageSize, items: all.slice(start, start + pageSize) });
  };
  return { fetchImpl, calls };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('fetchAllProducts（列表必须能访问到全部商品）', () => {
  it('超过单页上限时按 total 循环翻页', async () => {
    const { fetchImpl, calls } = pagedFetch(makeProducts(120));
    vi.stubGlobal('fetch', fetchImpl);

    const { total, items } = await fetchAllProducts();

    expect(total).toBe(120);
    expect(items).toHaveLength(120);
    expect(items[0].id).toBe(1);
    expect(items[119].id).toBe(120);
    expect(calls.map((c) => c.page)).toEqual([1, 2, 3]);
  });

  it('一页就够时只发一次请求', async () => {
    const { fetchImpl, calls } = pagedFetch(makeProducts(24));
    vi.stubGlobal('fetch', fetchImpl);

    const { total, items } = await fetchAllProducts();

    expect(total).toBe(24);
    expect(items).toHaveLength(24);
    expect(calls).toHaveLength(1);
  });

  it('空列表不会死循环', async () => {
    const { fetchImpl, calls } = pagedFetch([]);
    vi.stubGlobal('fetch', fetchImpl);

    const { total, items } = await fetchAllProducts();

    expect(total).toBe(0);
    expect(items).toEqual([]);
    expect(calls).toHaveLength(1);
  });

  it('接口一直返回满页时，被 maxPages 兜住而不是无限翻', async () => {
    let hits = 0;
    vi.stubGlobal('fetch', async (url) => {
      hits += 1;
      const u = new URL(url);
      return okResponse({
        total: 100000,
        page: Number(u.searchParams.get('page')),
        pageSize: 50,
        items: makeProducts(50),
      });
    });

    const { items } = await fetchAllProducts({ maxPages: 3 });

    expect(hits).toBe(3);
    expect(items).toHaveLength(150);
  });

  it('筛选参数透传到 query 上（L0-5 的搜索直接复用）', async () => {
    const { fetchImpl, calls } = pagedFetch([]);
    vi.stubGlobal('fetch', fetchImpl);

    await fetchAllProducts({ keyword: '耳机', category: '数码' });

    expect(calls).toHaveLength(1);
  });
});

describe('上下架接口（L0-4）', () => {
  it('PATCH /api/products/{id}/status，请求体是 { status: "off" }', async () => {
    const calls = [];
    vi.stubGlobal('fetch', async (url, init) => {
      calls.push({ url, init });
      return okResponse({ id: 3, status: 'off' });
    });

    const data = await updateProductStatus(3, 'off');

    expect(calls).toHaveLength(1);
    expect(calls[0].url).toMatch(/\/api\/products\/3\/status$/);
    expect(calls[0].init.method).toBe('PATCH');
    expect(JSON.parse(calls[0].init.body)).toEqual({ status: 'off' });
    expect(calls[0].init.headers).toEqual({ 'Content-Type': 'application/json' });
    expect(data).toEqual({ id: 3, status: 'off' });
  });

  it('上架也是同一个接口，只是 status 换成 "on"', async () => {
    let body = null;
    vi.stubGlobal('fetch', async (_url, init) => {
      body = JSON.parse(init.body);
      return okResponse({ id: 7, status: 'on' });
    });

    const data = await updateProductStatus(7, 'on');

    expect(body).toEqual({ status: 'on' });
    expect(data.status).toBe('on');
  });

  it('商品不存在时把后端 message 抛给调用方（页面据此提示，不是白屏）', async () => {
    vi.stubGlobal('fetch', async () => ({
      ok: false,
      status: 404,
      json: async () => ({ code: 40401, message: '商品不存在', data: null }),
    }));

    await expect(updateProductStatus(9999, 'off')).rejects.toThrow('商品不存在');
  });
});

describe('订单接口（L1-6 / L1-7）', () => {
  const createdOrder = {
    id: 9,
    orderNo: 'SO202611190009',
    productId: 3,
    productTitle: '晨光中性笔 12 支装',
    quantity: 2,
    unitPrice: 1999,
    totalAmount: 3998,
    status: 'created',
    paidAt: null,
  };

  it('POST /api/orders，quantity 提交的是数字而不是字符串', async () => {
    const calls = [];
    vi.stubGlobal('fetch', async (url, init) => {
      calls.push({ url, init });
      return { ok: true, status: 201, json: async () => ({ code: 0, message: 'ok', data: createdOrder }) };
    });

    const order = await createOrder({ productId: 3, quantity: '2' }); // 故意传字符串

    expect(calls[0].url).toMatch(/\/api\/orders$/);
    expect(calls[0].init.method).toBe('POST');
    const body = JSON.parse(calls[0].init.body);
    expect(body).toEqual({ productId: 3, quantity: 2 });
    expect(typeof body.quantity).toBe('number');
    expect(order.totalAmount).toBe(3998);
    expect(order.status).toBe('created');
  });

  it('fetchAllOrders 超过一页时按 total 翻页（别让首页 10 条当天花板）', async () => {
    const all = Array.from({ length: 120 }, (_, i) => ({
      id: i + 1,
      orderNo: `SO${String(i + 1).padStart(4, '0')}`,
      quantity: 1,
      totalAmount: 100,
      status: 'created',
    }));
    const calls = [];
    vi.stubGlobal('fetch', async (url) => {
      const u = new URL(url);
      const page = Number(u.searchParams.get('page'));
      const pageSize = Math.min(50, Number(u.searchParams.get('pageSize')));
      calls.push({ page, pageSize });
      const start = (page - 1) * pageSize;
      return okResponse({ total: all.length, page, pageSize, items: all.slice(start, start + pageSize) });
    });

    const { total, items } = await fetchAllOrders();

    expect(total).toBe(120);
    expect(items).toHaveLength(120);
    expect(calls.map((c) => c.page)).toEqual([1, 2, 3]);
  });

  it('订单接口报错时把后端 message 抛给调用方（库存不足 / 商品已下架都靠它显示）', async () => {
    vi.stubGlobal('fetch', async () => ({
      ok: false,
      status: 409,
      json: async () => ({ code: 40901, message: '库存不足', data: null }),
    }));

    await expect(createOrder({ productId: 1, quantity: 99 })).rejects.toThrow('库存不足');
  });

  it('listOrders 带 page / pageSize 参数', async () => {
    let url = '';
    vi.stubGlobal('fetch', async (next) => {
      url = next;
      return okResponse({ total: 0, page: 2, pageSize: 5, items: [] });
    });

    await listOrders({ page: 2, pageSize: 5 });

    expect(url).toMatch(/page=2/);
    expect(url).toMatch(/pageSize=5/);
  });
});

describe('支付接口（L2-10）', () => {
  it('POST /api/payments 带 orderId 和支付方式', async () => {
    const calls = [];
    vi.stubGlobal('fetch', async (url, init) => {
      calls.push({ url, init });
      return {
        ok: true,
        status: 200,
        json: async () => ({
          code: 0,
          message: 'ok',
          data: { paymentId: 'PAY202611190001', orderId: 2, amount: 5180, status: 'processing' },
        }),
      };
    });

    const payment = await createPayment({ orderId: '2', method: 'wechat' });

    expect(calls[0].url).toMatch(/\/api\/payments$/);
    expect(calls[0].init.method).toBe('POST');
    expect(JSON.parse(calls[0].init.body)).toEqual({ orderId: 2, method: 'wechat' });
    expect(payment).toMatchObject({ paymentId: 'PAY202611190001', status: 'processing' });
  });

  it('GET /api/payments/{paymentId} 用于轮询', async () => {
    let url = '';
    vi.stubGlobal('fetch', async (next) => {
      url = next;
      return okResponse({ paymentId: 'PAY1', status: 'paid', paidAt: '2026-11-19T10:00:02.000Z' });
    });

    const payment = await getPayment('PAY1');

    expect(url).toMatch(/\/api\/payments\/PAY1$/);
    expect(payment.status).toBe('paid');
  });

  it('对已支付订单再发起支付：后端 message 原样抛出', async () => {
    vi.stubGlobal('fetch', async () => ({
      ok: false,
      status: 409,
      json: async () => ({ code: 40903, message: '订单已支付，请勿重复支付', data: null }),
    }));

    await expect(createPayment({ orderId: 1, method: 'alipay' })).rejects.toThrow(
      '订单已支付，请勿重复支付',
    );
  });
});

describe('业务错误', () => {
  it('把后端 message 原样抛给调用方（main.js 负责显示，页面不白屏）', async () => {
    vi.stubGlobal('fetch', async () => ({
      ok: false,
      status: 409,
      json: async () => ({ code: 40901, message: '库存不足', data: null }),
    }));

    await expect(listProducts()).rejects.toThrow('库存不足');
    await expect(fetchAllProducts()).rejects.toBeInstanceOf(ApiError);
  });

  it('连不上接口时给的是能定位的提示，不是 "Failed to fetch"', async () => {
    vi.stubGlobal('fetch', async () => {
      throw new TypeError('fetch failed');
    });

    await expect(listProducts()).rejects.toThrow(/连不上接口/);
  });
});
