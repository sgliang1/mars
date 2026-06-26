package com.interstellar.admin.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态 SQL 查询构建器
 * 替代 Controller 层的 StringBuilder 拼接
 */
public class AdminQueryBuilder {

    private final StringBuilder selectSql = new StringBuilder();
    private final StringBuilder countSql = new StringBuilder();
    private final StringBuilder whereSql = new StringBuilder();
    private final List<Object> params = new ArrayList<>();
    private String orderByClause = "";
    private boolean hasWhere = false;

    private AdminQueryBuilder(String baseTable, String selectColumns) {
        this.selectSql.append("SELECT ").append(selectColumns).append(" FROM ").append(baseTable);
        this.countSql.append("SELECT COUNT(*) FROM ").append(baseTable);
    }

    public static AdminQueryBuilder from(String table, String columns) {
        return new AdminQueryBuilder(table, columns);
    }

    public AdminQueryBuilder join(String joinClause) {
        selectSql.append(" ").append(joinClause);
        countSql.append(" ").append(joinClause);
        return this;
    }

    public AdminQueryBuilder where(String condition, Object... values) {
        if (!hasWhere) {
            whereSql.append(" WHERE ");
            hasWhere = true;
        } else {
            whereSql.append(" AND ");
        }
        whereSql.append(condition);
        for (Object v : values) {
            params.add(v);
        }
        return this;
    }

    public AdminQueryBuilder whereIf(boolean condition, String clause, Object... values) {
        if (condition) {
            where(clause, values);
        }
        return this;
    }

    public AdminQueryBuilder like(String column, String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            where(column + " LIKE ?", "%" + keyword + "%");
        }
        return this;
    }

    public AdminQueryBuilder eq(String column, Object value) {
        if (value != null) {
            where(column + " = ?", value);
        }
        return this;
    }

    public AdminQueryBuilder range(String column, Object min, Object max) {
        if (min != null) {
            where(column + " >= ?", min);
        }
        if (max != null) {
            where(column + " <= ?", max);
        }
        return this;
    }

    public AdminQueryBuilder orderBy(String clause) {
        this.orderByClause = " ORDER BY " + clause;
        return this;
    }

    public String buildSelect(AdminQueryDTO query) {
        return selectSql.toString() + whereSql + orderByClause +
                " LIMIT " + query.getSize() + " OFFSET " + query.getOffset();
    }

    public String buildCount() {
        return countSql.toString() + whereSql;
    }

    public Object[] buildParams() {
        return params.toArray();
    }
}