package com.mars.admin.common;

import java.time.LocalDateTime;

/**
 * 管理端通用查询参数
 */
public class AdminQueryDTO {

    // 分页
    private int page = 1;
    private int size = 20;

    // 通用筛选
    private String keyword;
    private Integer status;

    // 时间范围
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // 数值范围
    private Integer minValue;
    private Integer maxValue;

    // 排序
    private String orderBy = "id";
    private String orderDir = "DESC";

    public int getPage() { return page; }
    public void setPage(int page) { this.page = Math.max(1, page); }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = Math.min(100, Math.max(1, size)); }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Integer getMinValue() { return minValue; }
    public void setMinValue(Integer minValue) { this.minValue = minValue; }

    public Integer getMaxValue() { return maxValue; }
    public void setMaxValue(Integer maxValue) { this.maxValue = maxValue; }

    public String getOrderBy() { return orderBy; }
    public void setOrderBy(String orderBy) { this.orderBy = orderBy; }

    public String getOrderDir() { return orderDir; }
    public void setOrderDir(String orderDir) {
        this.orderDir = "ASC".equalsIgnoreCase(orderDir) ? "ASC" : "DESC";
    }

    public int getOffset() {
        return (page - 1) * size;
    }
}