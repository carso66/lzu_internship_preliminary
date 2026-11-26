package com.example.demo.order;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

	private static final String COLUMNS =
			"id, order_no, product_id, product_title, quantity, unit_price, total_amount, "
					+ "buyer_name, buyer_phone, address, status, created_at, paid_at";

	private static final RowMapper<Order> ROW_MAPPER = (ResultSet rs, int rowNum) -> new Order(
			rs.getLong("id"),
			rs.getString("order_no"),
			rs.getLong("product_id"),
			rs.getString("product_title"),
			rs.getInt("quantity"),
			rs.getInt("unit_price"),
			rs.getInt("total_amount"),
			rs.getString("buyer_name"),
			rs.getString("buyer_phone"),
			rs.getString("address"),
			rs.getString("status"),
			toLocalDateTime(rs.getTimestamp("created_at")),
			toLocalDateTime(rs.getTimestamp("paid_at")));

	private final JdbcTemplate jdbcTemplate;

	public OrderRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * 条件更新扣库存，返回受影响行数。
	 *
	 * 把「库存够不够」写进 WHERE 而不是先 SELECT 再 UPDATE：
	 * 判断和扣减合成一条原子语句，两个请求同时下单也不会把库存扣成负数。
	 * 返回 0 就代表库存不足（或商品在判断之后被删掉了）。
	 */
	public int decreaseStock(long productId, int quantity) {
		return jdbcTemplate.update(
				"UPDATE products SET stock = stock - ?, updated_at = CURRENT_TIMESTAMP "
						+ "WHERE id = ? AND stock >= ?",
				quantity, productId, quantity);
	}

	/**
	 * 插入订单并回读整行。
	 *
	 * unit_price / total_amount 由调用方按数据库当前价格算好传进来，
	 * 不读请求体，客户端传的价格一律无效。
	 */
	public Order insert(OrderCreateRequest request, String orderNo, String productTitle, int unitPrice) {
		int totalAmount = unitPrice * request.quantity();
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(
					"INSERT INTO orders (order_no, product_id, product_title, quantity, unit_price, "
							+ "total_amount, buyer_name, buyer_phone, address, status) "
							+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'created')",
					new String[] { "id" });
			ps.setString(1, orderNo);
			ps.setLong(2, request.productId());
			ps.setString(3, productTitle);
			ps.setInt(4, request.quantity());
			ps.setInt(5, unitPrice);
			ps.setInt(6, totalAmount);
			ps.setString(7, request.buyerName());
			ps.setString(8, request.buyerPhone());
			ps.setString(9, request.address());
			return ps;
		}, keyHolder);

		Number id = keyHolder.getKey();
		if (id == null) {
			throw new IllegalStateException("插入订单后未取回主键");
		}
		return findById(id.longValue())
			.orElseThrow(() -> new IllegalStateException("插入订单后回读失败，id=" + id));
	}

	public Optional<Order> findById(long id) {
		List<Order> rows = jdbcTemplate.query(
				"SELECT " + COLUMNS + " FROM orders WHERE id = ?", ROW_MAPPER, id);
		return rows.stream().findFirst();
	}

	public Optional<Order> findByOrderNo(String orderNo) {
		List<Order> rows = jdbcTemplate.query(
				"SELECT " + COLUMNS + " FROM orders WHERE order_no = ?", ROW_MAPPER, orderNo);
		return rows.stream().findFirst();
	}

	/**
	 * 当前页订单，按创建时间倒序。
	 *
	 * 再补一个 id DESC：created_at 只精确到毫秒，同一时刻下的两笔订单
	 * 若不加次级排序键，翻页时顺序可能抖动，导致同一订单重复出现或漏掉。
	 */
	public List<Order> findPage(OrderListQuery query) {
		return jdbcTemplate.query(
				"SELECT " + COLUMNS + " FROM orders WHERE buyer_phone = ? "
						+ "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
				ROW_MAPPER, query.buyerPhone(), query.pageSize(), query.offset());
	}

	public long count(OrderListQuery query) {
		Long total = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orders WHERE buyer_phone = ?", Long.class, query.buyerPhone());
		return total == null ? 0L : total;
	}

	/** 取某前缀（SO + 日期）下已存在的最大订单号，用于生成下一个序号。 */
	public Optional<String> findMaxOrderNo(String prefix) {
		List<String> rows = jdbcTemplate.queryForList(
				"SELECT MAX(order_no) FROM orders WHERE order_no LIKE ?", String.class, prefix + "%");
		return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
	}

	private static java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toLocalDateTime();
	}
}
