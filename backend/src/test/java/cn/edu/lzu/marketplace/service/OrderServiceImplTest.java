package cn.edu.lzu.marketplace.service;

import cn.edu.lzu.marketplace.common.BusinessException;
import cn.edu.lzu.marketplace.common.ErrorCode;
import cn.edu.lzu.marketplace.dto.CreateOrderRequest;
import cn.edu.lzu.marketplace.dto.OrderPageResponse;
import cn.edu.lzu.marketplace.dto.OrderResponse;
import cn.edu.lzu.marketplace.model.Order;
import cn.edu.lzu.marketplace.model.PageResult;
import cn.edu.lzu.marketplace.model.Product;
import cn.edu.lzu.marketplace.repository.OrderRepository;
import cn.edu.lzu.marketplace.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderService 单元测试：Mock 掉两个 Repository，验证下单的业务分支
 * （404/40902/40901、服务端算价、乘法溢出）、扣库存先于插订单的顺序、
 * 订单号回填，以及列表分页的 long 偏移防溢出。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderServiceImpl service;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 24, 12, 0);

    private static Product product(long id, long price, int stock, String status) {
        return new Product(id, "考研数学复习全书", price, stock, "图书", null, status, "校园数码铺", NOW);
    }

    private static CreateOrderRequest request(long productId, int quantity) {
        return new CreateOrderRequest(productId, quantity, "张三", "13800138000", "兰州市城关区天水南路222号");
    }

    @Test
    void createComputesPriceFromServerAndDeductsBeforeInsert() {
        when(productRepository.findById(3L)).thenReturn(Optional.of(product(3L, 4990L, 10, Product.STATUS_ON)));
        when(productRepository.deductStockIfAvailable(3L, 2)).thenReturn(1);
        when(orderRepository.insert(any(Order.class))).thenReturn(42L);
        when(orderRepository.updateOrderNo(eq(42L), anyString())).thenReturn(1);

        OrderResponse resp = service.create(request(3L, 2));

        assertEquals(42L, resp.id());
        assertTrue(resp.orderNo().matches("SO\\d{8}000042"), "订单号应由自增主键派生: " + resp.orderNo());
        assertEquals(4990L, resp.unitPrice(), "单价必须来自服务端商品，而非客户端");
        assertEquals(9980L, resp.totalAmount(), "总额 = 服务端单价 × 数量");
        assertEquals(Order.STATUS_CREATED, resp.status());

        // 顺序：先扣库存，再插订单（保证事务内扣减先于写入）
        InOrder inOrder = inOrder(productRepository, orderRepository);
        inOrder.verify(productRepository).deductStockIfAvailable(3L, 2);
        inOrder.verify(orderRepository).insert(any(Order.class));

        // 插入的订单快照：标题 / 单价 / 总额均由服务端根据商品生成
        ArgumentCaptor<Order> cap = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).insert(cap.capture());
        Order saved = cap.getValue();
        assertEquals("考研数学复习全书", saved.productTitle());
        assertEquals(4990L, saved.unitPrice());
        assertEquals(9980L, saved.totalAmount());
        assertEquals(2, saved.quantity());
        assertEquals(Order.STATUS_CREATED, saved.status());
        assertNull(saved.paidAt());
        assertTrue(saved.orderNo().startsWith("P"), "插入时应使用临时占位订单号");

        // 回填的正式订单号与响应一致
        ArgumentCaptor<String> noCap = ArgumentCaptor.forClass(String.class);
        verify(orderRepository).updateOrderNo(eq(42L), noCap.capture());
        assertEquals(resp.orderNo(), noCap.getValue());
    }

    @Test
    void createWhenOrderNoUpdateAffectsZeroRowsThrows() {
        when(productRepository.findById(3L)).thenReturn(Optional.of(product(3L, 4990L, 10, Product.STATUS_ON)));
        when(productRepository.deductStockIfAvailable(3L, 2)).thenReturn(1);
        when(orderRepository.insert(any(Order.class))).thenReturn(42L);
        // 回填影响行数为 0：不得返回成功响应，必须抛运行时异常触发事务回滚
        when(orderRepository.updateOrderNo(eq(42L), anyString())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.create(request(3L, 2)));
        verify(orderRepository).updateOrderNo(eq(42L), anyString());
    }

    @Test
    void createWhenProductNotFoundReturns40401() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request(999L, 2)));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        verify(productRepository, never()).deductStockIfAvailable(anyLong(), anyInt());
        verify(orderRepository, never()).insert(any());
    }

    @Test
    void createWhenProductOffSaleReturns40902WithoutDeducting() {
        when(productRepository.findById(3L)).thenReturn(Optional.of(product(3L, 4990L, 10, Product.STATUS_OFF)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request(3L, 2)));

        assertEquals(ErrorCode.PRODUCT_OFF_SALE, ex.getErrorCode());
        assertEquals("商品已下架", ex.getMessage());
        verify(productRepository, never()).deductStockIfAvailable(anyLong(), anyInt());
    }

    @Test
    void createWhenDeductFailsByStockReturns40901() {
        // 首次读取为在售；条件更新影响 0 行；重读发现库存已不足
        when(productRepository.findById(3L))
                .thenReturn(Optional.of(product(3L, 4990L, 10, Product.STATUS_ON)))
                .thenReturn(Optional.of(product(3L, 4990L, 0, Product.STATUS_ON)));
        when(productRepository.deductStockIfAvailable(3L, 2)).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request(3L, 2)));

        assertEquals(ErrorCode.STOCK_INSUFFICIENT, ex.getErrorCode());
        verify(orderRepository, never()).insert(any());
    }

    @Test
    void createWhenDeductFailsByConcurrentOffSaleReturns40902() {
        // 首次读取在售；并发把他下架，条件更新 0 行；重读发现已下架
        when(productRepository.findById(3L))
                .thenReturn(Optional.of(product(3L, 4990L, 10, Product.STATUS_ON)))
                .thenReturn(Optional.of(product(3L, 4990L, 10, Product.STATUS_OFF)));
        when(productRepository.deductStockIfAvailable(3L, 2)).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request(3L, 2)));

        assertEquals(ErrorCode.PRODUCT_OFF_SALE, ex.getErrorCode());
        verify(orderRepository, never()).insert(any());
    }

    @Test
    void createWhenTotalAmountOverflowsReturns40001() {
        when(productRepository.findById(3L))
                .thenReturn(Optional.of(product(3L, Long.MAX_VALUE, 100, Product.STATUS_ON)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request(3L, 2)));

        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(productRepository, never()).deductStockIfAvailable(anyLong(), anyInt());
    }

    @Test
    void getByOrderNoWhenMissingReturns40401() {
        when(orderRepository.findByOrderNo("SO2026112400001")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getByOrderNo("SO2026112400001"));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        assertEquals("订单不存在", ex.getMessage());
    }

    @Test
    void getByOrderNoMapsFields() {
        Order o = new Order(1L, "SO20261124000001", 3L, "考研数学复习全书", 2, 4990L, 9980L,
                "张三", "13800138000", "地址", "created", NOW, null);
        when(orderRepository.findByOrderNo("SO20261124000001")).thenReturn(Optional.of(o));

        OrderResponse resp = service.getByOrderNo("SO20261124000001");

        assertEquals("SO20261124000001", resp.orderNo());
        assertEquals(9980L, resp.totalAmount());
        assertEquals("张三", resp.buyerName());
    }

    @Test
    void listMapsItemsAndPageFields() {
        Order o = new Order(1L, "SO20261124000001", 3L, "考研数学复习全书", 1, 4990L, 4990L,
                "张三", "13800138000", "地址", "created", NOW, null);
        when(orderRepository.findPage(0L, 10, "13800138000"))
                .thenReturn(new PageResult<>(1L, List.of(o)));

        OrderPageResponse resp = service.list(1, 10, "13800138000");

        assertEquals(1L, resp.total());
        assertEquals(1, resp.page());
        assertEquals(10, resp.pageSize());
        assertEquals(1, resp.items().size());
        assertEquals("SO20261124000001", resp.items().get(0).orderNo());
    }

    @Test
    void listUsesLongOffsetToAvoidIntegerOverflow() {
        when(orderRepository.findPage(anyLong(), anyInt(), anyString()))
                .thenReturn(new PageResult<>(0L, List.of()));

        service.list(Integer.MAX_VALUE, 50, "13800138000");

        ArgumentCaptor<Long> offsetCap = ArgumentCaptor.forClass(Long.class);
        verify(orderRepository).findPage(offsetCap.capture(), anyInt(), anyString());
        long offset = offsetCap.getValue();
        assertTrue(offset > 0, "偏移量不得因 int 溢出变为负数，实得: " + offset);
        assertEquals((long) (Integer.MAX_VALUE - 1) * 50, offset);
    }
}
