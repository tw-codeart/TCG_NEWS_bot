package com.twcodeart.tcgnews.source.rsshub;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsCategory;
import com.twcodeart.tcgnews.news.NewsItem;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class OfficialXCardFeedParser {

    private static final Logger log = LoggerFactory.getLogger(OfficialXCardFeedParser.class);
    private static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");
    private final Game game;
    private final String account;
    private final java.util.function.Predicate<String> postFilter;
    private final boolean allowWsImages;
    private final boolean requireImage;
    private final Pattern postPath;

    public OfficialXCardFeedParser(Game game, String account, java.util.function.Predicate<String> postFilter,
                                   boolean allowWsImages, boolean requireImage) {
        this.game = game;
        this.account = account;
        this.postFilter = postFilter;
        this.allowWsImages = allowWsImages;
        this.requireImage = requireImage;
        this.postPath = Pattern.compile("^/" + Pattern.quote(account) + "/status/(\\d+)/?$", Pattern.CASE_INSENSITIVE);
    }

    public List<NewsItem> parse(byte[] rawFeed) throws FeedException, IOException {
        SyndFeedInput input = new SyndFeedInput();
        input.setAllowDoctypes(false);
        input.setXmlHealerOn(false);
        List<NewsItem> items = new ArrayList<>();
        try (XmlReader reader = new XmlReader(new ByteArrayInputStream(rawFeed))) {
            var feed = input.build(reader);
            for (SyndEntry entry : feed.getEntries()) {
                try {
                    items.addAll(parseEntry(entry));
                } catch (IllegalArgumentException exception) {
                    log.warn("Skipping invalid Official X RSSHub entry: link={}, reason={}",
                            entry.getLink(), exception.getMessage());
                }
            }
            log.info("Official X RSSHub feed parsed: entries={}, cardRevealItems={}", feed.getEntries().size(), items.size());
        }
        return items;
    }

    private List<NewsItem> parseEntry(SyndEntry entry) {
        String title = plainHtml(entry.getTitle()).text();
        List<Document> bodies = new ArrayList<>();
        if (entry.getDescription() != null) {
            bodies.add(plainHtml(entry.getDescription().getValue()));
        }
        for (SyndContent content : entry.getContents()) {
            bodies.add(plainHtml(content.getValue()));
        }
        if (!postFilter.test(title + "\n" + bodies.stream().map(Document::text)
                .collect(java.util.stream.Collectors.joining("\n")))) { return List.of(); }
        if (!postFilter.test(title)) {
            title = bodies.stream().map(Document::text).filter(postFilter)
                    .findFirst().orElse(null);
        }
        if (title == null) {
            return List.of();
        }
        if (title.length() > 1000) {
            throw new IllegalArgumentException("Post text/title exceeds the existing 1000-character title limit");
        }
        String sourceUrl = postUrl(entry);
        Instant publishedAt = entry.getPublishedDate() == null ? null : entry.getPublishedDate().toInstant();
        LocalDate publishedDate = publishedAt == null ? null : publishedAt.atZone(JAPAN).toLocalDate();
        if (publishedAt == null) {
            log.warn("Official X RSSHub entry has no valid publication date: sourceUrl={}", sourceUrl);
        }
        List<String> images = new ArrayList<>();
        for (Document body : bodies) {
            for (var image : body.select("img")) {
                addImage(images, image.attr("src"), sourceUrl);
            }
        }
        for (var enclosure : entry.getEnclosures()) {
            if (enclosure.getType() != null && enclosure.getType().startsWith("image/")) {
                addImage(images, enclosure.getUrl(), sourceUrl);
            }
        }
        List<NewsItem> items = new ArrayList<>();
        if (images.isEmpty() && requireImage) {
            log.warn("Skipping image-free card announcement: sourceUrl={}", sourceUrl);
            return List.of();
        }
        if (images.isEmpty()) {
            log.warn("Official X RSSHub card post has no supported image URL: sourceUrl={}", sourceUrl);
            items.add(item(title, sourceUrl, publishedAt, publishedDate, null));
        } else {
            // Occurrences are emitted here; the existing ingestion policy owns
            // duplicate decisions if an attachment occurs in several feed fields.
            for (String imageUrl : images) {
                items.add(item(title, sourceUrl, publishedAt, publishedDate, imageUrl));
            }
        }
        return items;
    }

    private NewsItem item(String title, String sourceUrl, Instant publishedAt,
                          LocalDate publishedDate, String imageUrl) {
        return new NewsItem(game, NewsCategory.CARD_REVEAL, title, null,
                null, publishedDate, publishedAt, imageUrl, sourceUrl,
                PostImageContentHash.generate(game, sourceUrl, imageUrl));
    }

    private Document plainHtml(String html) {
        Document document = Jsoup.parseBodyFragment(html == null ? "" : html);
        document.select("script, style, iframe, blockquote").remove();
        return document;
    }

    private String postUrl(SyndEntry entry) {
        String candidate = entry.getLink();
        if (candidate == null || candidate.isBlank()) {
            candidate = entry.getUri();
        }
        if (candidate == null || candidate.isBlank()) {
            throw new IllegalArgumentException("Missing original X post URL");
        }
        URI uri = URI.create(candidate).normalize();
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || !(host.equalsIgnoreCase("x.com") || host.equalsIgnoreCase("twitter.com")
                || host.equalsIgnoreCase("www.x.com") || host.equalsIgnoreCase("www.twitter.com"))
                || uri.getUserInfo() != null || uri.getPort() != -1) {
            throw new IllegalArgumentException("Invalid original X post URL");
        }
        var match = postPath.matcher(uri.getPath());
        if (!match.matches()) {
            throw new IllegalArgumentException("Post URL does not belong to the configured official account");
        }
        String canonical = "https://x.com/" + account + "/status/" + match.group(1);
        if (canonical.length() > 2048) {
            throw new IllegalArgumentException("Oversized original post URL");
        }
        return canonical;
    }

    private void addImage(List<String> images, String value, String sourceUrl) {
        try {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing image URL");
            }
            URI uri = URI.create(value).normalize();
            boolean xMedia = "pbs.twimg.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPath().startsWith("/media/");
            boolean wsMedia = allowWsImages && "ws-tcg.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPath().startsWith("/wordpress/wp-content/uploads/");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || uri.getPort() != -1 || !(xMedia || wsMedia) || uri.toASCIIString().length() > 2048) {
                throw new IllegalArgumentException("Invalid or unsupported official media URL");
            }
            images.add(uri.toASCIIString());
        } catch (IllegalArgumentException exception) {
            log.warn("Ignoring Official X RSSHub image: sourceUrl={}, reason={}", sourceUrl, exception.getMessage());
        }
    }
}
