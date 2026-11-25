package com.example.marketplace.service;

import com.example.marketplace.mapper.HealthMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * 探活服务：数据库探测失败时返回不可用，不向调用方暴露底层异常。
 */
@Service
public class HealthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthService.class);

    private final HealthMapper healthMapper;

    public HealthService(HealthMapper healthMapper) {
        this.healthMapper = healthMapper;
    }

    public boolean isDatabaseReady() {
        try {
            return healthMapper.ping() == 1;
        } catch (DataAccessException exception) {
            LOGGER.warn("Database health check failed", exception);
            return false;
        }
    }
}
