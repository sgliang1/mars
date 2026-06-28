package com.interstellar.user.domain.legal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interstellar.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user/legal")
public class LegalDocumentController {

    @Autowired
    private LegalDocumentMapper legalDocumentMapper;

    @GetMapping("/privacy")
    public Result<Map<String, String>> getPrivacy() {
        return getLatestDoc("privacy");
    }

    @GetMapping("/terms")
    public Result<Map<String, String>> getTerms() {
        return getLatestDoc("terms");
    }

    private Result<Map<String, String>> getLatestDoc(String docType) {
        LegalDocument doc = legalDocumentMapper.selectOne(
                new LambdaQueryWrapper<LegalDocument>()
                        .eq(LegalDocument::getDocType, docType)
                        .orderByDesc(LegalDocument::getVersion)
                        .last("LIMIT 1"));
        if (doc == null) {
            return Result.fail("文档不存在");
        }
        Map<String, String> data = new HashMap<>();
        data.put("title", doc.getTitle());
        data.put("content", doc.getContent());
        data.put("version", doc.getVersion());
        return Result.success(data);
    }
}
