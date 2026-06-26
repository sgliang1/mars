package com.interstellar.common.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * XSS 内容净化工具 — 统一清理用户输入中的危险 HTML/JS
 *
 * 策略：社交平台用户不应提交任何 HTML 标签，全部纯文本化。
 * 对于帖子/评论/聊天消息，只保留纯文本内容（strip tags）。
 */
public final class SanitizeUtil {

    private SanitizeUtil() {}

    /**
     * 去除所有 HTML 标签，保留纯文本内容。
     * 适用于帖子标题、正文、评论、聊天消息等用户输入文本。
     *
     * 示例：
     *   "&lt;script&gt;alert('xss')&lt;/script&gt;hello" → "hello"
     *   "&lt;b&gt;bold&lt;/b&gt;" → "bold"
     *   "正常文字" → "正常文字"
     */
    public static String stripHtml(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        // 先 unescape 防止 &#60;script&#62; 等编码绕过，再 strip 所有标签
        String unescaped = org.jsoup.parser.Parser.unescapeEntities(input, false);
        return Jsoup.clean(unescaped, "", Safelist.none(), new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false)).trim();
    }

    /**
     * 转义 HTML 特殊字符（保留原始文本，但将 < > & " ' 转为实体）。
     * 适用于需要原样展示但不允许执行的场景。
     */
    public static String escapeHtml(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
