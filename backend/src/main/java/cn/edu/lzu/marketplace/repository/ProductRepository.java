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

    /**
     * 原子扣减库存：仅当商品处于上架状态且剩余库存充足时才扣减，
     * 返回受影响行数（1 表示扣减成功，0 表示因下架 / 库存不足 / 商品不存在而未扣减）。
     *
     * <p>更新条件同时校验 status 与剩余库存，避免「先查库存再无条件更新」的竞态：
     * 并发下单时由数据库在行级锁上重新评估条件，不会把库存扣成负数。
     */
    int deductStockIfAvailable(Long id, int quantity);
}
