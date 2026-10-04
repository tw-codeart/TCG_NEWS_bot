package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.news.CardRevealContentHash;
import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsCategory;
import com.twcodeart.tcgnews.news.NewsItem;
import com.twcodeart.tcgnews.product.Product;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WsCardRevealsParser {

    private static final Logger log = LoggerFactory.getLogger(WsCardRevealsParser.class);
    private static final Pattern REVEAL_DATE = Pattern.compile(
            "^今日のカード\\s*【\\s*(\\d{4})年\\s*(\\d{1,2})月\\s*(\\d{1,2})日[^】]*】");

    public List<NewsItem> parse(Document document, Product product) {
        List<NewsItem> items = new ArrayList<>();
        for (Element heading : document.select(".article__accordionTitle")) {
            String title = heading.text();
            if (!title.matches("^今日のカード\\s*【.*")) {
                continue; // Includes the undated archive wrapper and unrelated promotional sections.
            }

            LocalDate revealDate;
            Matcher matcher = REVEAL_DATE.matcher(title);
            try {
                if (!matcher.find()) {
                    throw new DateTimeException("Missing full reveal date");
                }
                revealDate = LocalDate.of(Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
            } catch (DateTimeException exception) {
                log.warn("Skipping WS reveal section with invalid date: sourceUrl={}, heading={}, reason={}",
                        product.getDetailUrl(), title, exception.getMessage());
                continue;
            }

            Element content = heading.nextElementSibling();
            if (content == null || !content.hasClass("article__accordionContent")) {
                log.warn("Skipping WS reveal section with missing content: sourceUrl={}, heading={}",
                        product.getDetailUrl(), title);
                continue;
            }

            for (Element entry : content.select("ul.img6Lists-card > li")) {
                if (entry.closest(".article__accordion") == heading.parent() && entry.select("img").isEmpty()) {
                    log.warn("Skipping WS card entry with missing image: sourceUrl={}, revealDate={}",
                            product.getDetailUrl(), revealDate);
                }
            }

            for (Element image : content.select("ul.img6Lists-card img")) {
                if (image.closest(".article__accordion") != heading.parent()) {
                    continue; // Nested dated accordions are handled with their own heading.
                }
                try {
                    URI imageUri = URI.create(image.absUrl("src")).normalize();
                    if (!"https".equalsIgnoreCase(imageUri.getScheme())
                            || !"ws-tcg.com".equalsIgnoreCase(imageUri.getHost())
                            || imageUri.getUserInfo() != null || imageUri.getPort() != -1
                            || imageUri.getPath() == null || imageUri.getPath().isBlank()) {
                        throw new IllegalArgumentException("Missing or non-official image URL");
                    }
                    String imageUrl = imageUri.toASCIIString();
                    String hash = CardRevealContentHash.generate(product.getDetailUrl(), revealDate, imageUrl);
                    items.add(new NewsItem(Game.WEISS_SCHWARZ, NewsCategory.CARD_REVEAL,
                            null, product.getProductName(), revealDate, imageUrl, product.getDetailUrl(), hash));
                } catch (IllegalArgumentException exception) {
                    log.warn("Skipping invalid WS card reveal: sourceUrl={}, revealDate={}, src={}, reason={}",
                            product.getDetailUrl(), revealDate, image.attr("src"), exception.getMessage());
                }
            }
        }
        return items;
    }
}
