package cn.edu.lzu.marketplace.repository;

import cn.edu.lzu.marketplace.model.PageResult;
import cn.edu.lzu.marketplace.model.Product;

import java.util.Optional;

/**
 * 商品数据访问接口。
 */
public interface ProductRepository {

    /**
     * 插入一条商品，返回自增主键。
     */
    Long insert(Product product);

    /**
     * 按主键查询，不存在返回空 Optional。
     */
    Optional<Product> findById(Long id);

    /**
     * 分页 + 组合筛选查询。
     *
     * @param offset   起始偏移量（从 0 开始），用 long 防止 (page-1)*pageSize 整数溢出
     * @param limit    每页条数
     * @param keyword  标题模糊匹配（子串语义，% 和 _ 会被转义），null/空白表示不筛选
     * @param status   状态精确匹配，null 表示全部
     * @param category 分类精确匹配，null/空白表示不筛选
     */
    PageResult<Product> findPage(long offset, int limit, String keyword, String status, String category);

    /**
     * 更新商品状态，返回受影响行数（0 表示商品不存在）。
     */
    int updateStatus(Long id, String status);
}
