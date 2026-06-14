-- 第一阶段目标库表（参考 DDL）
-- 说明：
-- 1. 当前项目尚未接入 Flyway / Liquibase，这里先提供目标结构草案
-- 2. 现有表以“补索引 + 新增扩展表”为主，避免一次性大改现有运行逻辑

-- =========================
-- auth: user profile
-- =========================
CREATE TABLE IF NOT EXISTS user_profile (
    user_id BIGINT NOT NULL COMMENT '与 user.id 一致',
    nickname VARCHAR(64) NULL COMMENT '展示昵称',
    avatar_url VARCHAR(255) NULL COMMENT '头像地址',
    bio VARCHAR(255) NULL COMMENT '个人简介',
    gender TINYINT NULL COMMENT '0未知 1男 2女',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id)
) COMMENT='用户资料表';

-- 建议为现有 user 表补唯一索引
-- ALTER TABLE user ADD UNIQUE KEY uk_user_username (username);
-- ALTER TABLE user ADD UNIQUE KEY uk_user_email (email);

-- =========================
-- post: topic
-- =========================
CREATE TABLE IF NOT EXISTS topic (
    id BIGINT NOT NULL AUTO_INCREMENT,
    slug VARCHAR(64) NOT NULL COMMENT '稳定路由标识',
    title VARCHAR(64) NOT NULL COMMENT '话题标题',
    summary VARCHAR(255) NULL COMMENT '话题摘要',
    keywords VARCHAR(255) NULL COMMENT '话题关键词，逗号分隔',
    highlight VARCHAR(255) NULL COMMENT '高亮文案',
    icon VARCHAR(64) NULL COMMENT '图标标识',
    sort_order INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0隐藏',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_topic_slug (slug),
    KEY idx_topic_status_sort (status, sort_order)
) COMMENT='社区话题表';

CREATE TABLE IF NOT EXISTS post_topic (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_topic (post_id, topic_id),
    KEY idx_post_topic_topic (topic_id, post_id)
) COMMENT='帖子话题关系表';

-- =========================
-- post: favorite / history
-- =========================
CREATE TABLE IF NOT EXISTS post_favorite (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_favorite_user_post (user_id, post_id),
    KEY idx_post_favorite_post (post_id)
) COMMENT='帖子收藏表';

CREATE TABLE IF NOT EXISTS post_browse_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    view_count INT NOT NULL DEFAULT 1,
    last_viewed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_history_user_post (user_id, post_id),
    KEY idx_post_history_last_viewed (user_id, last_viewed_at)
) COMMENT='帖子浏览历史表';

-- 建议为现有内容表补索引
-- ALTER TABLE post ADD KEY idx_post_user_time (user_id, create_time);
-- ALTER TABLE post ADD KEY idx_post_create_time (create_time);
-- ALTER TABLE post ADD COLUMN share_count INT NOT NULL DEFAULT 0 COMMENT '转发数' AFTER comment_count;
-- ALTER TABLE post_image ADD KEY idx_post_image_post_sort (post_id, sort);
-- ALTER TABLE post_like ADD UNIQUE KEY uk_post_like_user_post (user_id, post_id);
-- ALTER TABLE post_like ADD KEY idx_post_like_post (post_id);
-- ALTER TABLE comment ADD KEY idx_comment_post_time (post_id, create_time);
-- ALTER TABLE comment ADD KEY idx_comment_user_time (user_id, create_time);
-- ALTER TABLE comment ADD KEY idx_comment_parent (parent_id);

-- =========================
-- chat: conversation / notification
-- =========================
CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type VARCHAR(32) NOT NULL COMMENT 'public_channel/direct/group/system',
    biz_key VARCHAR(64) NULL COMMENT '业务主键，如 direct-1-2 / group-1001',
    title VARCHAR(128) NOT NULL,
    description VARCHAR(255) NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0关闭',
    last_message_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversation_type_biz (type, biz_key),
    KEY idx_conversation_last_message (last_message_at)
) COMMENT='会话表';

CREATE TABLE IF NOT EXISTS conversation_member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'member',
    unread_count INT NOT NULL DEFAULT 0,
    muted TINYINT NOT NULL DEFAULT 0,
    pinned TINYINT NOT NULL DEFAULT 0,
    archived TINYINT NOT NULL DEFAULT 0,
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_read_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversation_member (conversation_id, user_id),
    KEY idx_conversation_member_user (user_id)
) COMMENT='会话成员表';

CREATE TABLE IF NOT EXISTS conversation_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    sender_name VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    message_type TINYINT NOT NULL DEFAULT 0 COMMENT '0文本 1图片 2系统',
    delivery_status VARCHAR(32) NOT NULL DEFAULT 'sent',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conversation_message_time (conversation_id, created_at)
) COMMENT='会话消息表';

CREATE TABLE IF NOT EXISTS notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    category VARCHAR(32) NOT NULL COMMENT 'interaction/system/security/activity',
    title VARCHAR(128) NOT NULL,
    content VARCHAR(255) NOT NULL,
    source_type VARCHAR(32) NULL COMMENT 'post/comment/conversation/system',
    source_id VARCHAR(64) NULL COMMENT '来源主键',
    read_status TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_notification_user_read_time (user_id, read_status, created_at)
) COMMENT='通知中心表';