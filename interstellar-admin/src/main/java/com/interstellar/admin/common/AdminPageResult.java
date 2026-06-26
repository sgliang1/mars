package com.interstellar.admin.common;

import java.util.List;
import java.util.Map;

/**
 * 管理端分页结果
 */
public class AdminPageResult {

    private long total;
    private List<Map<String, Object>> records;
    private int page;
    private int size;

    public AdminPageResult(long total, List<Map<String, Object>> records, int page, int size) {
        this.total = total;
        this.records = records;
        this.page = page;
        this.size = size;
    }

    public long getTotal() { return total; }
    public List<Map<String, Object>> getRecords() { return records; }
    public int getPage() { return page; }
    public int getSize() { return size; }
}