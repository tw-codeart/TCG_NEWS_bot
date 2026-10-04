package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.product.Product;
import com.twcodeart.tcgnews.product.ProductRepository;
import com.twcodeart.tcgnews.source.NewsSourceException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * Discovers the product catalog for future news sources to use.
 * Catalog entries are products, not PRODUCT_NEWS announcements.
 */
@Service
public class WsProductDiscoveryService {

    public static final String PRODUCTS_URL = "https://ws-tcg.com/products/";
    private static final Logger log = LoggerFactory.getLogger(WsProductDiscoveryService.class);

    private final WsProductsParser parser;
    private final ProductRepository repository;

    public WsProductDiscoveryService(WsProductsParser parser, ProductRepository repository) {
        this.parser = parser;
        this.repository = repository;
    }

    public List<Product> discoverProducts() throws NewsSourceException {
        List<Product> products;
        try {
            Document document = Jsoup.connect(PRODUCTS_URL)
                    .userAgent("TCG-News-Bot/0.1 (+https://github.com/tw-codeart/TCG_NEWS_bot)")
                    .timeout(15_000)
                    .get();
            products = parser.parse(document);
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            log.error("Failed to discover WS products from {}", PRODUCTS_URL, exception);
            throw new NewsSourceException("Failed to discover WS products from " + PRODUCTS_URL, exception);
        }

        // The detail URL is the catalog identity. Repeated discovery updates that row.
        // News deduplication is a separate, planned service concern.
        return repository.saveAll(products);
    }
}
