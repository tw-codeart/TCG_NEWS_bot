package com.twcodeart.tcgnews.source.ws;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class WsInformationPageClient {

    public static final String INFORMATION_URL = "https://ws-tcg.com/information/";

    public Document fetch() throws IOException {
        return Jsoup.connect(INFORMATION_URL)
                .userAgent("TCG-News-Bot/0.1 (+https://github.com/tw-codeart/TCG_NEWS_bot)")
                .timeout(15_000)
                .get();
    }
}
