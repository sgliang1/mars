package com.mars.post.domain.post;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PostDTO {
    private String title;
    private String content; // 正文
    private String images;  // 图片URL，逗号分隔
    private List<Long> mentionUserIds; // @提及的用户ID列表
    private Integer visibility; // 可见范围: 0公开 1仅粉丝 2仅好友 3仅自己

    // Phase 3.3: 定时发布
    private LocalDateTime scheduledAt;  // 定时发布时间，null表示立即发布

    // Phase 4.4: 位置信息
    private String locationName;        // 位置名称
    private java.math.BigDecimal latitude;
    private java.math.BigDecimal longitude;

    // Phase 4.3: 投票
    private String pollQuestion;        // 投票问题
    private java.util.List<String> pollOptions; // 投票选项
    private Boolean pollMultiple;       // 是否多选
    private LocalDateTime pollExpireAt; // 投票截止时间

    // 话题关联
    private List<Long> topicIds;        // 关联的话题ID列表
}