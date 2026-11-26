package com.campus.marketplace.dto;

import java.util.ArrayList;
import java.util.List;

public class PageResult<T> {

    private long total;
    private int page;
    private int pageSize;
    private List<T> items;

    public PageResult() {
        this.items = new ArrayList<>();
    }

    public PageResult(long total, int page, int pageSize, List<T> items) {
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.items = items == null ? new ArrayList<>() : items;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }
}
