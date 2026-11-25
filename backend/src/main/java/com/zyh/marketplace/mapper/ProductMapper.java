package com.zyh.marketplace.mapper;

import com.zyh.marketplace.dto.ProductPageCriteria;
import com.zyh.marketplace.entity.Product;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * products 表的 SQL 层。
 * 所有入参一律使用 #{} 参数绑定（不拼接字符串），防止 SQL 注入。
 */
@Mapper
public interface ProductMapper {

    String COLUMNS = "id, title, price, stock, category, description, status, seller_name, created_at";

    @Insert("INSERT INTO products (title, price, stock, category, description, status, seller_name, created_at) "
            + "VALUES (#{title}, #{price}, #{stock}, #{category}, #{description}, #{status}, #{sellerName}, CURRENT_TIMESTAMP)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Product product);

    @Select("SELECT " + COLUMNS + " FROM products WHERE id = #{id}")
    Product findById(@Param("id") Long id);

    @Select("<script>"
            + "SELECT COUNT(*) FROM products"
            + "<where>"
            + "  <if test='criteria.keyword != null'> AND title LIKE CONCAT('%', #{criteria.keyword}, '%') ESCAPE '\\'</if>"
            + "  <if test='criteria.status != null'> AND status = #{criteria.status}</if>"
            + "  <if test='criteria.category != null'> AND category = #{criteria.category}</if>"
            + "</where>"
            + "</script>")
    long countByCriteria(@Param("criteria") ProductPageCriteria criteria);

    /**
     * 数据库分页（LIMIT ? OFFSET ?）+ 稳定排序：
     * created_at DESC, id DESC —— id 唯一，保证同创建时间的记录顺序固定，翻页不重不漏。
     */
    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM products"
            + "<where>"
            + "  <if test='criteria.keyword != null'> AND title LIKE CONCAT('%', #{criteria.keyword}, '%') ESCAPE '\\'</if>"
            + "  <if test='criteria.status != null'> AND status = #{criteria.status}</if>"
            + "  <if test='criteria.category != null'> AND category = #{criteria.category}</if>"
            + "</where>"
            + " ORDER BY created_at DESC, id DESC"
            + " LIMIT #{criteria.limit} OFFSET #{criteria.offset}"
            + "</script>")
    List<Product> findByCriteria(@Param("criteria") ProductPageCriteria criteria);

    /** 只更新 status；返回受影响行数（0 表示商品不存在） */
    @Update("UPDATE products SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 原子扣库存（L1-6 下单使用，L2-10 并发验证沿用同一约定）：
     * 把「库存充足」和「仍在架」两个条件合并进同一条 UPDATE，依赖数据库行锁串行化并发，
     * 不依赖 JVM 锁、不引入 Redis 等额外组件。
     * 受影响行数 = 1 → 扣减成功；= 0 → 商品不存在 / 已下架 / 库存不足三者之一，
     * 由调用方回查商品行区分（本接口不猜）。
     * 与 schema.sql 顶部注释、DESIGN.md 2.2 节约定的方案保持一致。
     */
    @Update("UPDATE products SET stock = stock - #{quantity} "
            + "WHERE id = #{id} AND stock >= #{quantity} AND status = 'on'")
    int deductStockIfAvailable(@Param("id") Long id, @Param("quantity") int quantity);
}
