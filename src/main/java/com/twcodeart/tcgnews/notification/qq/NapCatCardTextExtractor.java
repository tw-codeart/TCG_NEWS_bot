package com.twcodeart.tcgnews.notification.qq;

import com.twcodeart.tcgnews.notification.ocr.CardTextExtractor;

import java.util.ArrayList;
import java.util.Map;

import static com.twcodeart.tcgnews.notification.qq.QqNotificationException.Reason.API_FAILURE;

/** NapCat's Windows OCR; language coverage and accuracy must be verified live. */
final class NapCatCardTextExtractor implements CardTextExtractor {
    private final QqApiClient api;

    NapCatCardTextExtractor(QqApiClient api) {
        this.api = api;
    }

    @Override
    public String extract(String imageUrl) {
        var texts = api.call("ocr_image", Map.of("image", imageUrl)).path("texts");
        if (!texts.isArray()) {
            throw new QqNotificationException(API_FAILURE, "QQ OCR returned an invalid text list");
        }
        var lines = new ArrayList<String>();
        for (var entry : texts) {
            var text = entry.path("text");
            if (text.isTextual() && !text.asText().isBlank()) {
                lines.add(text.asText().strip());
            }
        }
        return String.join("\n", lines);
    }
}
