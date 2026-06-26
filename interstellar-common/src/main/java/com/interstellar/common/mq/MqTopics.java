package com.interstellar.common.mq;

/**
 * RocketMQ Topic 和 Tag 常量定义
 */
public final class MqTopics {

    private MqTopics() {}

    // ==================== 通知 Topic ====================
    /** 互动通知（点赞、评论、关注） */
    public static final String NOTIFICATION = "INTERSTELLAR_NOTIFICATION";
    public static final String TAG_INTERACTION = "INTERACTION";
    public static final String TAG_SYSTEM = "SYSTEM";

    // ==================== 聊天消息 Topic ====================
    /** 聊天消息持久化和离线补推 */
    public static final String CHAT_MESSAGE = "INTERSTELLAR_CHAT_MESSAGE";
    public static final String TAG_PERSIST = "PERSIST";
    public static final String TAG_OFFLINE = "OFFLINE";

    // ==================== 搜索同步 Topic ====================
    /** ES 索引同步 */
    public static final String SEARCH_SYNC = "INTERSTELLAR_SEARCH_SYNC";
    public static final String TAG_POST = "POST";
    public static final String TAG_USER = "USER";

    // ==================== 计数器回写 Topic ====================
    /** 计数器定期回写 DB（目前用定时任务，预留 Topic） */
    public static final String COUNT_SYNC = "INTERSTELLAR_COUNT_SYNC";

    // ==================== 死信队列 ====================
    /** 超过最大重试次数的失败消息 */
    public static final String DEAD_LETTER = "INTERSTELLAR_DEAD_LETTER";
}