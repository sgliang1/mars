# 数据模型第一阶段目标

## 目标

- 保持当前服务边界不变：`mars-auth`、`mars-post`、`mars-chat`
- 不做大规模服务拆分，先补齐后续功能会依赖的核心数据模型
- 让“内容域 / 社区域 / 消息域”从前端占位逐步迁移到后端真实落库

## 服务归属

### `mars-auth`

- 保留当前 `user`
- 新增 `user_profile`
- 用于承载昵称、头像、简介、状态等资料字段，避免当前登录注册逻辑一次性大改

### `mars-post`

- 保留当前 `post`、`post_content`、`post_image`、`post_like`、`comment`
- 新增 `topic`
- 新增 `post_topic`
- 新增 `post_favorite`
- 新增 `post_browse_history`

这样可以先把“内容发布 / 详情 / 评论 / 收藏 / 浏览历史 / 话题聚合”补成完整闭环。

### `mars-chat`

- 保留当前 `chat_message`，继续承载公共频道消息
- 新增 `conversation`
- 新增 `conversation_member`
- 新增 `conversation_message`
- 新增 `notification`

这样可以把当前前端本地会话壳逐步替换成后端真实模型，同时不影响现有公共频道能力。

## 当前表设计判断

### 已经合理的部分

- `post` / `post_content` / `post_image` 的拆分方向正确
- `post_like` 独立成关系表是合理的
- `comment.parent_id` 足够支撑基础回复链路

### 明显偏薄的部分

- `user` 只有登录字段，不够支撑正式资料页
- `post` 缺少内容状态、可见性、话题归属等字段
- `comment` 缺少 root/reply-to 维度、状态字段
- `chat_message` 只能支撑一个公共频道，不足以承载完整消息中心

## 第一阶段表目标

### 内容域

- `post`：内容摘要、作者快照、统计字段
- `post_content`：长正文
- `post_image`：图片列表
- `comment`：评论与回复
- `post_like`：点赞关系
- `post_favorite`：收藏关系
- `post_browse_history`：浏览历史

### 社区域

- `topic`：话题基础信息 + 关键词标签
- `post_topic`：帖子与话题关系

### 账号域

- `user`
- `user_profile`

### 消息域

- `chat_message`：公共频道
- `conversation`：会话元数据
- `conversation_member`：成员状态
- `conversation_message`：会话消息
- `notification`：通知中心

## 实施顺序

1. 先应用 `docs/stage1_target_schema.sql` 中新增表
2. 后端逐步接入 `favorite` / `history` / `topic`
3. 前端把本地 `favorites/history` 迁到真实接口
4. 再推进 `conversation` / `notification` 后端化

## 暂不处理

- 群组资料、群公告、群权限细节表
- 复杂推荐 / 排序 / 运营配置表
- 审核流、举报流、风控流

这些适合在第二阶段继续补，不影响当前主链路推进。