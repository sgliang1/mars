package com.mars.post.domain.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReportDTO {
    @NotBlank(message = "目标类型不能为空")
    private String targetType;    // post / comment / user

    @NotNull(message = "目标ID不能为空")
    private Long targetId;

    @NotBlank(message = "举报原因不能为空")
    private String reason;

    private String description;   // 选填
}
