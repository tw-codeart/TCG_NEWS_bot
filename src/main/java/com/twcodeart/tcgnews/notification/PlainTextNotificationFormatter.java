package com.twcodeart.tcgnews.notification;

import com.twcodeart.tcgnews.news.NewsCategory;
import com.twcodeart.tcgnews.news.NewsItem;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class PlainTextNotificationFormatter implements NotificationFormatter {

    @Override
    public String format(NewsItem newsItem) {
        Objects.requireNonNull(newsItem, "newsItem must not be null");
        String game = switch (newsItem.getGame()) {
            case WEISS_SCHWARZ -> "Weiß Schwarz";
            case YUGIOH_OCG -> "Yu-Gi-Oh! OCG";
        };
        String category = switch (newsItem.getCategory()) {
            case CARD_REVEAL -> "Card Reveal";
            case PRODUCT_NEWS -> "Product News";
            case CARD_LIST_NEWS -> "Card List News";
            case NEWS -> "News";
        };
        List<String> sections = new ArrayList<>();
        sections.add("[" + game + " " + category + "]");

        List<String> details = new ArrayList<>();
        addField(details, "Product", newsItem.getProductName());
        boolean cardReveal = newsItem.getCategory() == NewsCategory.CARD_REVEAL;
        LocalDate date = cardReveal ? newsItem.getRevealDate() : newsItem.getPublishedDate();
        if (date != null) {
            addField(details, cardReveal ? "Reveal date" : "Published date", date.toString());
        } else if (newsItem.getGame() == com.twcodeart.tcgnews.news.Game.YUGIOH_OCG && newsItem.getPublishedDate() != null) {
            addField(details, "Published date", newsItem.getPublishedDate().toString());
        }
        addField(details, "Title", newsItem.getTitle());
        if (!details.isEmpty()) {
            sections.add(String.join("\n", details));
        }
        // The model requires sourceUrl; optional image URLs are links, never fetched.
        sections.add("Official source:\n" + newsItem.getSourceUrl().strip());
        if (hasText(newsItem.getImageUrl())) {
            sections.add("Image:\n" + newsItem.getImageUrl().strip());
        }
        return String.join("\n\n", sections);
    }

    private void addField(List<String> details, String label, String value) {
        if (hasText(value)) {
            details.add(label + ": " + value.strip());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
