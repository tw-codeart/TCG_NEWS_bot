package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.product.Product;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

@Component
public class WsProductsParser {

    private static final Logger log = LoggerFactory.getLogger(WsProductsParser.class);
    private static final String PRODUCT_SECTIONS = ".products__latestSwiper, .products__lists";
    private static final String PRODUCT_LINKS =
            ".products__latestSwiper a.products__link, .products__lists a.products__link";

    public List<Product> parse(Document document) {
        if (document.select(PRODUCT_SECTIONS).isEmpty()) {
            throw new IllegalStateException("WS products page is missing expected product sections");
        }

        Elements links = document.select(PRODUCT_LINKS);
        List<Product> products = new ArrayList<>();
        for (Element link : links) {
            // The live carousel includes an empty name paragraph before the real name.
            String name = link.select(".products__name").stream()
                    .map(Element::text)
                    .filter(text -> !text.isBlank())
                    .findFirst()
                    .orElse("");
            if (name.isBlank()) {
                log.warn("Skipping WS product with missing name: href={}", link.attr("href"));
                continue;
            }

            String detailUrl = resolveDetailUrl(link);
            if (detailUrl == null) {
                log.warn("Skipping WS product with invalid detail URL: href={}", link.attr("href"));
                continue;
            }
            products.add(new Product(Game.WEISS_SCHWARZ, name, detailUrl));
        }

        if (!links.isEmpty() && products.isEmpty()) {
            throw new IllegalStateException("WS products page contains no valid product entries");
        }
        return products;
    }

    private String resolveDetailUrl(Element link) {
        if (link.attr("href").isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(link.absUrl("href")).normalize();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"ws-tcg.com".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1
                    || uri.getPath() == null
                    || !uri.getPath().matches("/products/[^/]+/?")) {
                return null;
            }
            String path = uri.getPath();
            if (!path.endsWith("/")) {
                path += "/";
            }
            return new URI("https", "ws-tcg.com", path, null).toString();
        } catch (URISyntaxException exception) {
            log.warn("Invalid WS product URL syntax: href={}", link.attr("href"), exception);
            return null;
        }
    }
}
