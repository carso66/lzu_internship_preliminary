package com.example.demo.repository;

import com.example.demo.dto.CreateProductRequest;
import com.example.demo.model.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import com.example.demo.common.PageResult;
import java.util.ArrayList;
import java.util.List;
import java.sql.PreparedStatement;
import java.util.Optional;

@Repository
public class ProductRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 插入商品，并返回数据库生成的商品 ID
    public long insert(CreateProductRequest request) {
        String sql = """
                INSERT INTO products (
                    title,
                    price,
                    stock,
                    category,
                    description,
                    seller_name,
                    image_url
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement =
                    connection.prepareStatement(sql, new String[]{"id"});

            statement.setString(1, request.title());
            statement.setLong(2, request.price());
            statement.setInt(3, request.stock());
            statement.setString(4, request.category());

            statement.setString(
                    5,
                    request.description() == null ? "" : request.description()
            );

            statement.setString(6, request.sellerName());

            statement.setString(
                    7,
                    request.imageUrl() == null ? "" : request.imageUrl()
            );

            return statement;
        }, keyHolder);

        Number id = keyHolder.getKey();

        if (id == null) {
            throw new IllegalStateException("新增商品后未取得商品 ID");
        }

        return id.longValue();
    }
    public PageResult<Product> findPage(
            String keyword,
            String status,
            String category,
            int page,
            int pageSize
    ) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND title LIKE ? ESCAPE '!'");

            // 将用户输入中的通配符作为普通字符搜索
            String escaped = keyword
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");

            params.add("%" + escaped + "%");
        }

        if (status != null) {
            where.append(" AND status = ?");
            params.add(status);
        }

        if (category != null) {
            where.append(" AND category = ?");
            params.add(category);
        }

        // 使用相同的筛选条件，统计总数
        String countSql = "SELECT COUNT(*) FROM products" + where;

        Long total = jdbcTemplate.queryForObject(
                countSql,
                Long.class,
                params.toArray()
        );

        // 使用 long 计算，避免大页码导致 int 乘法溢出
        long offset = ((long) page - 1) * pageSize;

        String listSql = """
            SELECT id, title, price, stock, category, description,
                   image_url, status, seller_name, created_at, updated_at
            FROM products
            """ + where + """
            
            ORDER BY created_at DESC, id DESC
            LIMIT ? OFFSET ?
            """;

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add(offset);

        List<Product> items = jdbcTemplate.query(
                listSql,
                (rs, rowNum) -> new Product(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getLong("price"),
                        rs.getInt("stock"),
                        rs.getString("category"),
                        rs.getString("description"),
                        rs.getString("image_url"),
                        rs.getString("status"),
                        rs.getString("seller_name"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime()
                ),
                pageParams.toArray()
        );

        return new PageResult<>(
                total == null ? 0 : total,
                page,
                pageSize,
                items
        );
    }
    public int updateStatus(long id, String status) {
        String sql = """
            UPDATE products
            SET status = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

        return jdbcTemplate.update(sql, status, id);
    }
    // 按 ID 查询商品；不存在时返回 Optional.empty()
    public Optional<Product> findById(long id) {
        String sql = """
                SELECT id, title, price, stock, category, description,
                       image_url, status, seller_name, created_at, updated_at
                FROM products
                WHERE id = ?
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new Product(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getLong("price"),
                        rs.getInt("stock"),
                        rs.getString("category"),
                        rs.getString("description"),
                        rs.getString("image_url"),
                        rs.getString("status"),
                        rs.getString("seller_name"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime()
                ), id
        ).stream().findFirst();
    }

    /**
     * 在事务中按 ID 查询并锁定商品行。
     *
     * 必须由处于事务中的 Service 方法调用，否则行锁会在当前语句
     * 结束后释放。
     */
    public Optional<Product> findByIdForUpdate(long id) {
        String sql = """
                SELECT id, title, price, stock, category, description,
                       image_url, status, seller_name, created_at, updated_at
                FROM products
                WHERE id = ?
                FOR UPDATE
                """;

        return jdbcTemplate.query(
                        sql,
                        (rs, rowNum) -> new Product(
                                rs.getLong("id"),
                                rs.getString("title"),
                                rs.getLong("price"),
                                rs.getInt("stock"),
                                rs.getString("category"),
                                rs.getString("description"),
                                rs.getString("image_url"),
                                rs.getString("status"),
                                rs.getString("seller_name"),
                                rs.getTimestamp("created_at").toLocalDateTime(),
                                rs.getTimestamp("updated_at").toLocalDateTime()
                        ),
                        id
                )
                .stream()
                .findFirst();
    }

    /**
     * 原子扣减库存。
     *
     * 返回值：
     * 1：扣减成功
     * 0：库存不足
     *
     * WHERE stock >= ? 防止库存被扣成负数。
     */
    public int deductStock(long productId, int quantity) {
        String sql = """
                UPDATE products
                SET stock = stock - ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND stock >= ?
                """;

        return jdbcTemplate.update(
                sql,
                quantity,
                productId,
                quantity
        );
    }
}
