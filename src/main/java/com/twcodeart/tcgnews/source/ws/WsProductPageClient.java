package com.twcodeart.tcgnews.source.ws;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Retrieves product HTML only. Jsoup does not fetch referenced images.
 */
@Component
public class WsProductPageClient {

    public Document fetch(String detailUrl) throws IOException {
        return Jsoup.connect(detailUrl)
                .userAgent("TCG-News-Bot/0.1 (+https://github.com/tw-codeart/TCG_NEWS_bot)")
                .timeout(15_000)
                .get();
    }
}
