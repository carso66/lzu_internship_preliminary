package com.lzu.xy;

import com.lzu.xy.domain.Order;
import com.lzu.xy.repository.OrderRepository;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class XyApplicationTests {

    @Resource
    OrderRepository orderRepository;
    @Test
    void contextLoads() {
        Iterable<Order> all = orderRepository.findAll();
        all.forEach(System.out::println);
    }

}
