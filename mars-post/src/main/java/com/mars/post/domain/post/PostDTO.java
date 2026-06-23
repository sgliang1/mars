package com.mars.post.domain.post;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PostDTO {
    @NotBlank(message = "帖子标题不能为空")
    @Size(max = 128, message = "标题长度不能超过 128 个字符")
    private String title;

    @NotBlank(message = "帖子内容不能为空")
    @Size(max = 5000, message = "内容长度不能超过 5000 个字符")
    private String content;

    @Size(max = 2000, message = "图片链接过长")
    private String images;

    @Size(max = 50, message = "@提及用户数不能超过 50 个")
    private List<Long> mentionUserIds;

    @Min(value = 0, message = "可见范围值无效")
    @Max(value = 3, message = "可见范围值无效")
    private Integer visibility;

    @Future(message = "定时发布时间必须是未来时间")
    private LocalDateTime scheduledAt;

    @Size(max = 128, message = "位置名称不能超过 128 个字符")
    private String locationName;

    @DecimalMin(value = "-90", message = "纬度范围 -90 到 90")
    @DecimalMax(value = "90", message = "纬度范围 -90 到 90")
    private BigDecimal latitude;

    @DecimalMin(value = "-180", message = "经度范围 -180 到 180")
    @DecimalMax(value = "180", message = "经度范围 -180 到 180")
    private BigDecimal longitude;

    @Size(max = 256, message = "投票问题不能超过 256 个字符")
    private String pollQuestion;

    @Size(min = 2, max = 10, message = "投票选项需为 2-10 个")
    private List<String> pollOptions;

    private Boolean pollMultiple;

    @Future(message = "投票截止时间必须是未来时间")
    private LocalDateTime pollExpireAt;

    @Size(max = 5, message = "关联话题不能超过 5 个")
    private List<Long> topicIds;
}
