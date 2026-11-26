package com.example.demo.order;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.common.ApiException;
import com.example.demo.product.Product;
import com.example.demo.product.ProductRepository;

@Service
public class OrderService {

	private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
	private static final int ORDER_NO_MAX_RETRY = 5;

	private final OrderRepository orderRepository;
	private final ProductRepository productRepository;

	public OrderService(OrderRepository orderRepository, ProductRepository productRepository) {
		this.orderRepository = orderRepository;
		this.productRepository = productRepository;
	}

	/**
	 * L1-6 下单。
	 *
	 * 整个方法在一个事务里：扣库存与插订单要么都成功，要么都回滚。
	 * 插单时若撞上 order_no 唯一约束，抛出的异常会让已扣的库存一起回滚，
	 * 不会留下「库存少了但订单不存在」的脏数据。
	 */
	@Transactional
	public Order create(OrderCreateRequest request) {
		Product product = productRepository.findById(request.productId())
			.orElseThrow(() -> ApiException.notFound("商品不存在"));

		if ("off".equals(product.status())) {
			throw ApiException.productOffShelf();
		}

		// 先查一次库存只是为了给出「库存不足」而不是笼统的冲突；
		// 真正的防超卖靠下面那条带条件的 UPDATE。
		if (product.stock() < request.quantity()) {
			throw ApiException.stockNotEnough();
		}
		if (orderRepository.decreaseStock(product.id(), request.quantity()) == 0) {
			throw ApiException.stockNotEnough();
		}

		return insertWithRetry(request, product);
	}

	/**
	 * 订单号撞车时重试几次。
	 *
	 * 序号是「查当日最大号 + 1」算出来的，两个并发事务可能读到同一个最大号，
	 * 唯一约束 uk_orders_order_no 会挡住后到的那个。捕获后重新取号，
	 * 并把 attempt 作为偏移量加进去，保证下一次尝试用的是不同的号。
	 */
	private Order insertWithRetry(OrderCreateRequest request, Product product) {
		DuplicateKeyException last = null;
		for (int attempt = 0; attempt < ORDER_NO_MAX_RETRY; attempt++) {
			try {
				String orderNo = nextOrderNo(attempt);
				return orderRepository.insert(request, orderNo, product.title(), product.price());
			}
			catch (DuplicateKeyException ex) {
				last = ex;
			}
		}
		throw last;
	}

	/**
	 * 订单号格式：SO + yyyyMMdd + 4 位当日序号，例如 SO202611190001。
	 *
	 * 序号超过 9999 时 %04d 会自然变长，订单号仍然唯一。
	 */
	private String nextOrderNo(int attempt) {
		String prefix = "SO" + LocalDate.now().format(DAY);
		int next = orderRepository.findMaxOrderNo(prefix)
			.map(max -> parseSequence(max, prefix) + 1)
			.orElse(1);
		return prefix + String.format("%04d", next + attempt);
	}

	private static int parseSequence(String orderNo, String prefix) {
		String tail = orderNo.substring(prefix.length());
		try {
			return Integer.parseInt(tail);
		}
		catch (NumberFormatException ex) {
			// 库里存在手工写入的、不符合规则的订单号，退化为从 1 重新计数
			return 0;
		}
	}

	/** L1-7 订单详情：不存在即 404 + 40401。 */
	@Transactional(readOnly = true)
	public Order get(String orderNo) {
		return orderRepository.findByOrderNo(orderNo)
			.orElseThrow(() -> ApiException.notFound("订单不存在"));
	}

	/** L1-8 按手机号查订单列表，按创建时间倒序。 */
	@Transactional(readOnly = true)
	public OrderPage list(OrderListQuery query) {
		long total = orderRepository.count(query);
		if (total == 0) {
			return new OrderPage(0, query.page(), query.pageSize(), List.of());
		}
		return new OrderPage(total, query.page(), query.pageSize(), orderRepository.findPage(query));
	}
}
