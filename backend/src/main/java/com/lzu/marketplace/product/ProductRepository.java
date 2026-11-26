package com.lzu.marketplace.product;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

/** 数据访问层：将请求中的 Java 字段映射到数据库列，并把查询结果还原为商品对象。 */
@Repository
public class ProductRepository {

    // 行映射复用：发布回读和列表查询共用同一套列到对象的转换。
    private static final RowMapper<Product> ROW_MAPPER = (rs, rowNum) ->
            new Product(rs.getLong("id"), rs.getString("title"), rs.getLong("price"),
                    rs.getInt("stock"), rs.getString("category"), rs.getString("description"),
                    rs.getString("status"), rs.getString("seller_name"),
                    rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert insert;

    public ProductRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        // 指定写入列，并让数据库生成 id；created_at 使用数据库的默认创建时间。
        this.insert = new SimpleJdbcInsert(jdbc)
                .withTableName("products")
                .usingColumns("title", "price", "stock", "category", "description", "seller_name", "status")
                .usingGeneratedKeyColumns("id");
    }

    public Product create(CreateProductRequest request) {
        // 参数由 JDBC 绑定，不拼接成 SQL；商品初始状态由服务端固定为 on。
        long id = insert.executeAndReturnKey(Map.of(
                "title", request.title(),
                "price", request.price(),
                "stock", request.stock(),
                "category", request.category(),
                "description", request.description() == null ? "" : request.description(),
                "seller_name", request.sellerName(),
                "status", "on"
        )).longValue();

        // 按刚生成的主键回读，使响应包含数据库实际保存的值和默认创建时间。
        return jdbc.queryForObject("SELECT * FROM products WHERE id = ?", ROW_MAPPER, id);
    }

    public java.util.Optional<Product> find(long id, boolean lock) {
        // 下单在事务内持有行锁，直到扣库存和插入订单一起提交。
        return jdbc.query("SELECT * FROM products WHERE id = ?" + (lock ? " FOR UPDATE" : ""),
                ROW_MAPPER, id).stream().findFirst();
    }

    public int updateStatus(long id, String status) {
        return jdbc.update("UPDATE products SET status = ? WHERE id = ?", status, id);
    }

    public int deductStock(long id, int quantity) {
        return jdbc.update("UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ? AND status = 'on'",
                quantity, id, quantity);
    }

    public ProductPage list(ProductQuery query) {
        // 动态拼接筛选条件；三个筛选用 AND 连接可叠加，参数一律交给 JDBC 绑定。
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (query.keyword() != null) {
            conditions.add("title LIKE ?");
            params.add("%" + query.keyword() + "%");
        }
        if (query.status() != null) {
            conditions.add("status = ?");
            params.add(query.status());
        }
        if (query.category() != null) {
            conditions.add("category = ?");
            params.add(query.category());
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

        // total 是筛选后的总条数，供前端计算总页数。
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM products" + where, Long.class, params.toArray());

        // 按 id 倒序：最新发布的排前，且主键唯一保证分页稳定不重不漏。
        int page = query.page();
        int pageSize = query.pageSize();
        // 偏移用 long 计算，避免 page 很大时 (page-1)*pageSize 溢出 int。
        long offset = (long) (page - 1) * pageSize;
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add(offset);
        List<Product> items = jdbc.query(
                "SELECT * FROM products" + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, pageParams.toArray());

        return new ProductPage(total, page, pageSize, items);
    }
}
