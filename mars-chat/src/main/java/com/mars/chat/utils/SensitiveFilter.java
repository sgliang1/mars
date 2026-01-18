package com.mars.chat.utils;

import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.Set;

@Component
public class SensitiveFilter {
    
    // 模拟敏感词库，实际应从数据库或配置文件加载
    private static final Set<String> SENSITIVE_WORDS = new HashSet<>();
    
    static {
        SENSITIVE_WORDS.add("炸弹");
        SENSITIVE_WORDS.add("赌博");
        SENSITIVE_WORDS.add("高利贷");
        // ... 添加更多
    }

    /**
     * 简单过滤：将敏感词替换为 **
     */
    public String filter(String text) {
        if (text == null) return "";
        for (String word : SENSITIVE_WORDS) {
            if (text.contains(word)) {
                text = text.replace(word, "**");
            }
        }
        return text;
    }
}