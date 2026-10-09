package com.twcodeart.tcgnews.notification.ocr;

/** Returns unverified text from an original card image, without guessing its rules. */
public interface CardTextExtractor {
    String extract(String imageUrl);
}
