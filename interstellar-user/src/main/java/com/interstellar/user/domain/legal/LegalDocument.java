package com.interstellar.user.domain.legal;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("legal_document")
public class LegalDocument {
    @TableId
    private Long id;
    private String docType;
    private String title;
    private String content;
    private String version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
