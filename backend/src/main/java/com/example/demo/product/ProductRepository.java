package com.example.demo.product;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {

	private static final String COLUMNS =
			"id, title, price, stock, category, description, status, seller_name, created_at";

	private static final RowMapper<Product> ROW_MAPPER = (ResultSet rs, int rowNum) -> new Product(
			rs.getLong("id"),
			rs.getString("title"),
			rs.getInt("price"),
			rs.getInt("stock"),
			rs.getString("category"),
			rs.getString("description"),
			rs.getString("status"),
			rs.getString("seller_name"),
			toLocalDateTime(rs.getTimestamp("created_at")));

	private final JdbcTemplate jdbcTemplate;

	public ProductRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** 插入商品并回读整行，返回值即接口响应里的 data。 */
	public Product insert(ProductCreateRequest request) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			// 只索取 id 列：H2 对 RETURN_GENERATED_KEYS 会把整行（含默认值列）都返回。
			PreparedStatement ps = connection.prepareStatement(
					"INSERT INTO products (title, price, stock, category, description, status, seller_name) "
							+ "VALUES (?, ?, ?, ?, ?, 'on', ?)",
					new String[] { "id" });
			ps.setString(1, request.title());
			ps.setInt(2, request.price());
			ps.setInt(3, request.stock());
			ps.setString(4, request.category());
			ps.setString(5, request.description());
			ps.setString(6, request.sellerName());
			return ps;
		}, keyHolder);

		Number id = keyHolder.getKey();
		if (id == null) {
			throw new IllegalStateException("插入商品后未取回主键");
		}
		return findById(id.longValue())
			.orElseThrow(() -> new IllegalStateException("插入商品后回读失败，id=" + id));
	}

	public Optional<Product> findById(long id) {
		List<Product> rows = jdbcTemplate.query(
				"SELECT " + COLUMNS + " FROM products WHERE id = ?", ROW_MAPPER, id);
		return rows.stream().findFirst();
	}

	/** 当前页数据。排序按 id 升序，与前端「新商品排在最后」的约定一致。 */
	public List<Product> findPage(ProductListQuery query) {
		StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS).append(" FROM products");
		List<Object> args = new ArrayList<>();
		appendFilters(sql, args, query);
		// LIMIT / OFFSET 用占位符传值，避免把用户输入拼进 SQL
		sql.append(" ORDER BY id ASC LIMIT ? OFFSET ?");
		args.add(query.pageSize());
		args.add(query.offset());

		return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
	}

	/** 与 {@link #findPage} 相同筛选条件下的总条数。 */
	public long count(ProductListQuery query) {
		StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM products");
		List<Object> args = new ArrayList<>();
		appendFilters(sql, args, query);

		Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
		return total == null ? 0L : total;
	}

	/** 只允许改 status（on/off），同时刷新 updated_at。 */
	public int updateStatus(long id, String status) {
		return jdbcTemplate.update(
				"UPDATE products SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
				status, id);
	}

	private static void appendFilters(StringBuilder sql, List<Object> args, ProductListQuery query) {
		List<String> conditions = new ArrayList<>();
		if (query.keyword() != null) {
			// 用 LOCATE 做「包含」匹配：不需要转义 % 和 _，且大小写不敏感
			conditions.add("LOCATE(LOWER(?), LOWER(title)) > 0");
			args.add(query.keyword());
		}
		if (query.status() != null) {
			conditions.add("status = ?");
			args.add(query.status());
		}
		if (query.category() != null) {
			conditions.add("category = ?");
			args.add(query.category());
		}
		if (!conditions.isEmpty()) {
			sql.append(" WHERE ").append(String.join(" AND ", conditions));
		}
	}

	private static java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toLocalDateTime();
	}
}
