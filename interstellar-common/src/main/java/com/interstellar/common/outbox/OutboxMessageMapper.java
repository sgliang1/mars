package com.interstellar.common.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 发件箱消息 Mapper
 */
@Mapper
public interface OutboxMessageMapper extends BaseMapper<OutboxMessage> {

    /**
     * 查询待发送的消息（支持多实例并发安全）
     * - status=0 (PENDING) 或 status=2 (FAILED 且到了重试时间)
     * - 按创建时间排序，取 batchSize 条
     */
    @Select("""
            SELECT * FROM outbox_message
            WHERE status IN (0, 2)
              AND (next_retry_at IS NULL OR next_retry_at <= NOW())
            ORDER BY created_at
            LIMIT #{batchSize}
            """)
    List<OutboxMessage> findPending(@Param("batchSize") int batchSize);
}
