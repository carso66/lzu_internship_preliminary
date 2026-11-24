package com.lzu.marketplace.product;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ProductRepository {

    private static final String COLUMNS =
            "id, title, price, stock, category, description, status, seller_name, created_at";

    private static final RowMapper<Product> ROW_MAPPER = (rs, rowNum) -> new Product(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getLong("price"),
            rs.getInt("stock"),
            rs.getString("category"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getString("seller_name"),
            rs.getObject("created_at", OffsetDateTime.class).withOffsetSameInstant(ZoneOffset.UTC)
    );

    private final NamedParameterJdbcTemplate jdbc;

    public ProductRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(CreateProductRequest request) {
        String sql = """
                INSERT INTO products (title, price, stock, category, description, seller_name)
                VALUES (:title, :price, :stock, :category, :description, :sellerName)
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("title", request.title())
                .addValue("price", request.price())
                .addValue("stock", request.stock())
                .addValue("category", request.category())
                .addValue("description", request.description())
                .addValue("sellerName", request.sellerName());
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, parameters, keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("插入商品后未取得主键");
        }
        return key.longValue();
    }

    public Optional<Product> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM products WHERE id = :id",
                        Map.of("id", id), ROW_MAPPER)
                .stream().findFirst();
    }

    public long count(ProductListQuery query) {
        Filter filter = buildFilter(query);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM products" + filter.where(),
                filter.parameters(), Long.class);
        return total == null ? 0 : total;
    }

    public List<Product> findPage(ProductListQuery query) {
        Filter filter = buildFilter(query);
        filter.parameters()
                .addValue("limit", query.getPageSize())
                .addValue("offset", query.offset());
        return jdbc.query("SELECT " + COLUMNS + " FROM products" + filter.where()
                        + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset",
                filter.parameters(), ROW_MAPPER);
    }

    public int updateStatus(long id, String status) {
        return jdbc.update("UPDATE products SET status = :status WHERE id = :id",
                Map.of("id", id, "status", status));
    }

    private Filter buildFilter(ProductListQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (query.getKeyword() != null) {
            where.append(" AND title LIKE :keyword ESCAPE '!'");
            String literal = query.getKeyword()
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");
            parameters.addValue("keyword", "%" + literal + "%");
        }
        if (query.getStatus() != null) {
            where.append(" AND status = :status");
            parameters.addValue("status", query.getStatus());
        }
        if (query.getCategory() != null) {
            where.append(" AND category = :category");
            parameters.addValue("category", query.getCategory());
        }
        return new Filter(where.toString(), parameters);
    }

    private record Filter(String where, MapSqlParameterSource parameters) {
    }
}
