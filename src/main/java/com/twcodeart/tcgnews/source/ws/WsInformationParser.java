package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.InformationContentHash;
import com.twcodeart.tcgnews.news.NewsCategory;
import com.twcodeart.tcgnews.news.NewsItem;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WsInformationParser {

    private static final Logger log = LoggerFactory.getLogger(WsInformationParser.class);
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("uuuu.MM.dd")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern PRODUCT_TITLE = Pattern.compile(
            "^(?:トライアルデッキ|ブースターパック|プレミアムブースター|エクストラブースター)\\s*"
                    + "(?:［([^［］]+)］|\\[([^\\[\\]]+)\\])\\s*(?:箔押しサインカード)?(?:公開|掲載)[！!]?$");

    public List<NewsItem> parse(Document document) {
        if (document.select("ul.news__lists").isEmpty()) {
            throw new IllegalStateException("WS information page is missing the expected news list");
        }
        List<NewsItem> items = new ArrayList<>();
        var entries = document.select("ul.news__lists > li.news__item");
        for (Element entry : entries) {
            try {
                Element link = entry.selectFirst("a.news__itemLink");
                if (link == null) {
                    throw new IllegalArgumentException("Missing news link");
                }
                Element titleElement = link.selectFirst(".news__itemTitle");
                if (titleElement == null || titleElement.text().isBlank() || titleElement.text().length() > 1000) {
                    throw new IllegalArgumentException("Missing or oversized news title");
                }
                String title = titleElement.text();
                String sourceUrl = officialUrl(link, "href");
                NewsCategory category = category(link);
                LocalDate publishedDate = publishedDate(link, sourceUrl);
                String imageUrl = imageUrl(link, sourceUrl);
                String productName = productName(category, title);
                String hash = InformationContentHash.generate(category, sourceUrl, title, publishedDate, imageUrl);
                items.add(new NewsItem(Game.WEISS_SCHWARZ, category, title, productName,
                        null, publishedDate, imageUrl, sourceUrl, hash));
            } catch (IllegalArgumentException exception) {
                log.warn("Skipping invalid WS information entry: text={}, reason={}",
                        entry.text(), exception.getMessage());
            }
        }
        return items;
    }

    private NewsCategory category(Element link) {
        var labels = link.select(".news__itemCatItem").eachText();
        if (labels.contains("カードリスト")) {
            return NewsCategory.CARD_LIST_NEWS;
        }
        if (labels.contains("商品情報")) {
            return NewsCategory.PRODUCT_NEWS;
        }
        return NewsCategory.NEWS;
    }

    private LocalDate publishedDate(Element link, String sourceUrl) {
        Element time = link.selectFirst("time.news__itemDate");
        if (time == null) {
            return null;
        }
        String machineDate = time.attr("datetime");
        if (!machineDate.isBlank()) {
            try {
                return LocalDate.parse(machineDate);
            } catch (DateTimeParseException exception) {
                log.warn("Invalid WS news datetime: sourceUrl={}, datetime={}", sourceUrl, machineDate);
            }
        }
        if (!time.text().isBlank()) {
            try {
                return LocalDate.parse(time.text(), DISPLAY_DATE);
            } catch (DateTimeParseException exception) {
                log.warn("Invalid WS news display date: sourceUrl={}, date={}", sourceUrl, time.text());
            }
        }
        return null;
    }

    private String imageUrl(Element link, String sourceUrl) {
        Element image = link.selectFirst(".news__itemThumb img");
        if (image == null) {
            return null;
        }
        try {
            return officialUrl(image, "src");
        } catch (IllegalArgumentException exception) {
            log.warn("Ignoring invalid WS news thumbnail: sourceUrl={}, src={}, reason={}",
                    sourceUrl, image.attr("src"), exception.getMessage());
            return null;
        }
    }

    private String productName(NewsCategory category, String title) {
        if (category != NewsCategory.PRODUCT_NEWS && category != NewsCategory.CARD_LIST_NEWS) {
            return null;
        }
        Matcher matcher = PRODUCT_TITLE.matcher(title);
        if (!matcher.matches()) {
            return null;
        }
        String name = (matcher.group(1) == null ? matcher.group(2) : matcher.group(1)).trim();
        return name.isEmpty() ? null : name;
    }

    private String officialUrl(Element element, String attribute) {
        if (element.attr(attribute).isBlank()) {
            throw new IllegalArgumentException("Missing " + attribute + " URL");
        }
        URI uri = URI.create(element.absUrl(attribute)).normalize();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"ws-tcg.com".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1) {
            throw new IllegalArgumentException("Invalid or non-official " + attribute + " URL");
        }
        // Preserve query parameters: expansion IDs are part of card-list identity.
        String url = uri.toASCIIString();
        if (url.length() > 2048) {
            throw new IllegalArgumentException("Oversized " + attribute + " URL");
        }
        return url;
    }
}
