package com.interstellar.common.push;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 推送负载
 */
@Data
public class PushPayload {
    /** 通知标题 */
    private String title;
    /** 通知正文 */
    private String body;
    /** 分类: interaction / chat / system */
    private String category;
    /** 点击后跳转路由 (如 /detail/123) */
    private String clickAction;
    /** 附加数据 */
    private Map<String, String> data = new HashMap<>();

    public static PushPayload of(String title, String body, String category) {
        PushPayload p = new PushPayload();
        p.setTitle(title);
        p.setBody(body);
        p.setCategory(category);
        return p;
    }

    public PushPayload withClickAction(String action) {
        this.clickAction = action;
        return this;
    }

    public PushPayload withData(String key, String value) {
        this.data.put(key, value);
        return this;
    }
}