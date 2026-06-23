package com.mars.common.cache;

import java.time.Duration;

/**
 * Redis 缓存 Key 常量与 TTL 定义
 * 所有 Key 统一使用 mars: 前缀
 */
public final class CacheKeys {

    private CacheKeys() {}

    // ==================== 用户相关 ====================
    /** 用户基础信息 */
    public static final String USER_INFO = "mars:user:info:";
    public static final Duration USER_INFO_TTL = Duration.ofMinutes(30);

    /** 用户资料（含扩展信息） */
    public static final String USER_PROFILE = "mars:user:profile:";
    public static final Duration USER_PROFILE_TTL = Duration.ofMinutes(30);

    /** 用户信用分 */
    public static final String USER_CREDIT = "mars:user:credit:";
    public static final Duration USER_CREDIT_TTL = Duration.ofHours(1);

    // ==================== 帖子相关 ====================
    /** 热门帖子列表 */
    public static final String POST_HOT_LIST = "mars:post:hot:list:";
    public static final Duration POST_HOT_LIST_TTL = Duration.ofMinutes(5);

    /** 帖子详情 */
    public static final String POST_DETAIL = "mars:post:detail:";
    public static final Duration POST_DETAIL_TTL = Duration.ofMinutes(10);

    // ==================== 社交关系 ====================
    /** 关注列表（Set） */
    public static final String RELATION_FOLLOWING = "mars:relation:following:";
    public static final Duration RELATION_FOLLOWING_TTL = Duration.ofMinutes(15);

    /** 关系状态检查 */
    public static final String RELATION_CHECK = "mars:relation:check:";
    public static final Duration RELATION_CHECK_TTL = Duration.ofMinutes(5);

    /** 拉黑状态检查 */
    public static final String RELATION_BLOCK = "mars:relation:block:";
    public static final Duration RELATION_BLOCK_TTL = Duration.ofMinutes(15);

    /** 拉黑用户 ID 集合（用于跨服务过滤） */
    public static final String RELATION_BLOCK_IDS = "mars:relation:block_ids:";
    public static final Duration RELATION_BLOCK_IDS_TTL = Duration.ofHours(24);

    /** 静音用户 ID 集合（用于跨服务过滤） */
    public static final String RELATION_MUTE_IDS = "mars:relation:mute_ids:";
    public static final Duration RELATION_MUTE_IDS_TTL = Duration.ofHours(24);

    /** 消息权限设置 */
    public static final String RELATION_MSG_SETTING = "mars:relation:msg_setting:";
    public static final Duration RELATION_MSG_SETTING_TTL = Duration.ofHours(24);

    /** 帖子可见性设置 */
    public static final String RELATION_POST_VISIBLE = "mars:relation:post_visible:";
    public static final Duration RELATION_POST_VISIBLE_TTL = Duration.ofHours(24);

    /** 资料可见性设置 */
    public static final String RELATION_PROFILE_VISIBLE = "mars:relation:profile_visible:";
    public static final Duration RELATION_PROFILE_VISIBLE_TTL = Duration.ofHours(24);

    // ==================== 计数器（持久化） ====================
    /** 帖子点赞计数 */
    public static final String COUNT_LIKES = "mars:count:likes:";

    /** 帖子评论计数 */
    public static final String COUNT_COMMENTS = "mars:count:comments:";

    /** 用户粉丝计数 */
    public static final String COUNT_FOLLOWERS = "mars:count:followers:";

    // ==================== 会话相关 ====================
    /** 会话摘要列表 */
    public static final String CONV_SUMMARIES = "mars:conv:summaries:";
    public static final Duration CONV_SUMMARIES_TTL = Duration.ofMinutes(2);

    // ==================== WebSocket ====================
    /** 用户在线状态 */
    public static final String WS_ONLINE = "mars:ws:online:";
    public static final Duration WS_ONLINE_TTL = Duration.ofMinutes(2);

    /** 消息 ACK 状态 */
    public static final String WS_ACK = "mars:ws:ack:";
    public static final Duration WS_ACK_TTL = Duration.ofHours(24);

    // ==================== 搜索 ====================
    /** 搜索防重锁 */
    public static final String SEARCH_LOCK = "mars:search:lock:";
    public static final Duration SEARCH_LOCK_TTL = Duration.ofSeconds(1);

    // ==================== 工具方法 ====================
    /** 构建 Key: prefix + id */
    public static String key(String prefix, Object id) {
        return prefix + id;
    }

    /** 构建关系检查 Key: prefix + id1 + : + id2 */
    public static String relationKey(String prefix, Object id1, Object id2) {
        return prefix + id1 + ":" + id2;
    }
}