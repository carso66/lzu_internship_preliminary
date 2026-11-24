package com.lzu.marketplace.mapper;

import com.lzu.marketplace.pojo.Product;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 商品 Mapper（注解式 SQL，列名与 products 表一致）。
 */
@Mapper
public interface ProductMapper {

    String COLUMNS = "id, title, price, stock, category, description, status, seller_name, created_at";

    @Insert("INSERT INTO products (title, price, stock, category, description, status, seller_name) "
            + "VALUES (#{title}, #{price}, #{stock}, #{category}, #{description}, #{status}, #{sellerName})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Product product);

    @Select("SELECT " + COLUMNS + " FROM products WHERE id = #{id}")
    Product findById(@Param("id") Long id);

    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM products"
            + "<where>"
            + "<if test=\"keyword != null and keyword != ''\"> AND title LIKE '%' || #{keyword} || '%'</if>"
            + "<if test=\"status != null and status != ''\"> AND status = #{status}</if>"
            + "<if test=\"category != null and category != ''\"> AND category = #{category}</if>"
            + "</where>"
            + " ORDER BY id DESC"
            + " LIMIT #{pageSize} OFFSET #{offset}"
            + "</script>")
    List<Product> page(@Param("keyword") String keyword,
                       @Param("status") String status,
                       @Param("category") String category,
                       @Param("offset") long offset,
                       @Param("pageSize") int pageSize);

    @Select("<script>"
            + "SELECT COUNT(*) FROM products"
            + "<where>"
            + "<if test=\"keyword != null and keyword != ''\"> AND title LIKE '%' || #{keyword} || '%'</if>"
            + "<if test=\"status != null and status != ''\"> AND status = #{status}</if>"
            + "<if test=\"category != null and category != ''\"> AND category = #{category}</if>"
            + "</where>"
            + "</script>")
    long count(@Param("keyword") String keyword,
               @Param("status") String status,
               @Param("category") String category);

    @Update("UPDATE products SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
