package com.twcodeart.tcgnews.notification.qq;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Adapts the shared formatter's output for QQ without altering stored source data. */
final class QqForwardMessageFormatter {
    private static final Pattern LINKS = Pattern.compile("(?i)(?:https?://|www\\.)\\S+"
            + "|(?<![\\w@.-])(?:[a-z0-9-]+\\.)+[a-z]{2,}/\\S+"
            + "|(?<![\\w@.-])(?:yugioh-card\\.com|ws-tcg\\.com|x\\.com|twitter\\.com|t\\.co)(?![\\w.-])");

    String withoutLinks(String message) {
        String text = LINKS.matcher(message).replaceAll("");
        text = text.lines().filter(line -> !line.strip().equals("Official source:")
                && !line.strip().equals("Image:")).collect(Collectors.joining("\n")).strip();
        return limit(text, 6000);
    }

    String ocrText(String text, boolean enabled) {
        if (!enabled) return "OCR 未启用；请查看原卡图。";
        if (text == null || text.isBlank()) return "OCR 未识别到文字或识别失败；请查看原卡图。";
        return "OCR 原文（未校对，可能有误；请以原卡图为准）：\n" + withoutLinks(text);
    }

    private String limit(String text, int count) {
        return text.codePointCount(0, text.length()) <= count ? text
                : text.substring(0, text.offsetByCodePoints(0, count)) + "\n[Text truncated]";
    }
}
