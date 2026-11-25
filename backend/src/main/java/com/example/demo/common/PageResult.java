package com.example.demo.common;

import java.util.List;

public record PageResult<T>(
        long total,
        int page,
        int pageSize,
        List<T> items
) {
}