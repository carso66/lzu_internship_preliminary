package com.lzu.market.order;

import com.lzu.market.common.ApiException;
import com.lzu.market.common.BizNoGenerator;
import com.lzu.market.product.Product;
import com.lzu.market.product.ProductDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 下单是本项目唯一的跨表写操作，放在 Service 里靠 @Transactional 保证原子性：
 * 减库存与写订单要么都成，要么都不成。ApiException 是 RuntimeException，抛出去照样回滚。
 */
@Service
public class OrderService {

    private final OrderDao orderDao;
    private final ProductDao productDao;
    private final BizNoGenerator bizNoGenerator;

    public OrderService(OrderDao orderDao, ProductDao productDao, BizNoGenerator bizNoGenerator) {
        this.orderDao = orderDao;
        this.productDao = productDao;
        this.bizNoGenerator = bizNoGenerator;
    }

    @Transactional
    public Order create(CreateOrderReq req) {
        long productId = req.productId();
        int quantity = req.quantity();

        Product product = productDao.findById(productId)
                .orElseThrow(() -> ApiException.notFound("商品不存在"));

        if (!"on".equals(product.status())) {
            throw ApiException.productOffShelf();
        }

        // 条件更新：库存够才减得动，不需要先查后改，也就没有并发窗口
        if (productDao.deductStock(productId, quantity) == 0) {
            throw ApiException.stockNotEnough();
        }

        // 价格快照取自库里的商品，客户端传什么都不采信
        int unitPrice = product.price();
        long totalAmount = Math.multiplyExact((long) unitPrice, (long) quantity);

        String orderNo = bizNoGenerator.next("SO");
        long id = orderDao.insert(orderNo, productId, product.title(), quantity,
                unitPrice, totalAmount, req.buyerName().trim(), req.buyerPhone(), req.address().trim());

        return orderDao.findById(id)
                .orElseThrow(() -> new ApiException(500, 50000, "订单写入后读不到"));
    }
}
