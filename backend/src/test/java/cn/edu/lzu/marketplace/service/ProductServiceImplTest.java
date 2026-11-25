package cn.edu.lzu.marketplace.service;

import cn.edu.lzu.marketplace.common.BusinessException;
import cn.edu.lzu.marketplace.common.ErrorCode;
import cn.edu.lzu.marketplace.dto.ProductCreateRequest;
import cn.edu.lzu.marketplace.dto.ProductPageResponse;
import cn.edu.lzu.marketplace.dto.ProductResponse;
import cn.edu.lzu.marketplace.dto.ProductStatusRequest;
import cn.edu.lzu.marketplace.dto.ProductStatusResponse;
import cn.edu.lzu.marketplace.model.PageResult;
import cn.edu.lzu.marketplace.model.Product;
import cn.edu.lzu.marketplace.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProductService 单元测试：Mock 掉 Repository，验证业务规则
 * （默认状态、时间戳写入、404 判定、分页偏移计算、字段映射）。
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository repository;

    @InjectMocks
    private ProductServiceImpl service;

    // 商品发布与详情：校验初始字段、响应映射及商品不存在时的业务码。
    @Test
    void createSetsDefaultStatusOnAndWritesTimestamp() {
        ProductCreateRequest req =
                new ProductCreateRequest("测试商品", 1990L, 10, "文具", "备注", "校园数码铺");
        when(repository.insert(any(Product.class))).thenReturn(42L);

        ProductResponse resp = service.create(req);

        assertEquals(42L, resp.id());
        assertEquals("on", resp.status());

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(repository).insert(captor.capture());
        Product saved = captor.getValue();
        assertNull(saved.id(), "插入前不应有主键");
        assertEquals(Product.STATUS_ON, saved.status(), "默认状态应为 on");
        assertEquals(1990L, saved.price());
        assertEquals(10, saved.stock());
        assertNotNull(saved.createdAt(), "创建时间应由服务端写入");
    }

    @Test
    void getByIdWhenMissingThrows40401() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(999L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        assertEquals("商品不存在", ex.getMessage());
    }

    @Test
    void getByIdMapsFieldsToResponse() {
        Product p = new Product(1L, "标题", 1990L, 10, "分类", "备注", "on", "卖家",
                LocalDateTime.of(2026, 11, 24, 12, 0));
        when(repository.findById(1L)).thenReturn(Optional.of(p));

        ProductResponse resp = service.getById(1L);

        assertEquals(1L, resp.id());
        assertEquals("标题", resp.title());
        assertEquals(1990L, resp.price());
    }

    @Test
    void listComputesOffsetAndMapsItems() {
        Product p = new Product(1L, "标题", 1990L, 10, "分类", null, "on", "卖家", LocalDateTime.now());
        when(repository.findPage(0, 10, null, null, null))
                .thenReturn(new PageResult<>(1L, List.of(p)));

        ProductPageResponse resp = service.list(1, 10, null, null, null);

        assertEquals(1L, resp.total());
        assertEquals(1, resp.page());
        assertEquals(10, resp.pageSize());
        assertEquals(1, resp.items().size());
        assertEquals(1L, resp.items().get(0).id());
    }

    // 上下架：检查成功时的响应字段和更新未命中时的错误码。
    @Test
    void updateStatusReturnsIdAndStatus() {
        when(repository.updateStatus(1L, "off")).thenReturn(1);

        ProductStatusResponse resp = service.updateStatus(1L, new ProductStatusRequest("off"));

        assertEquals(1L, resp.id());
        assertEquals("off", resp.status());
    }

    @Test
    void updateStatusWhenNoRowAffectedThrows40401() {
        when(repository.updateStatus(999L, "off")).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateStatus(999L, new ProductStatusRequest("off")));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void listUsesLongOffsetToAvoidIntegerOverflow() {
        // 检查大页码按 long 计算偏移量，在乘以页大小时保持正确结果。
        when(repository.findPage(anyLong(), anyInt(), any(), any(), any()))
                .thenReturn(new PageResult<>(0L, List.of()));

        service.list(Integer.MAX_VALUE, 50, null, null, null);

        ArgumentCaptor<Long> offsetCap = ArgumentCaptor.forClass(Long.class);
        verify(repository).findPage(offsetCap.capture(), anyInt(), any(), any(), any());
        long offset = offsetCap.getValue();
        assertTrue(offset > 0, "偏移量不得因 int 溢出变为负数，实得: " + offset);
        assertEquals((long) (Integer.MAX_VALUE - 1) * 50, offset);
    }
}
