package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsItem;
import com.twcodeart.tcgnews.product.Product;
import com.twcodeart.tcgnews.product.ProductRepository;
import com.twcodeart.tcgnews.source.NewsSource;
import com.twcodeart.tcgnews.source.NewsSourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class WsCardRevealSource implements NewsSource {

    private static final Logger log = LoggerFactory.getLogger(WsCardRevealSource.class);
    private final ProductRepository products;
    private final WsProductPageClient client;
    private final WsCardRevealsParser parser;

    public WsCardRevealSource(ProductRepository products, WsProductPageClient client, WsCardRevealsParser parser) {
        this.products = products;
        this.client = client;
        this.parser = parser;
    }

    @Override
    public String getSourceId() {
        return "ws-jp-card-reveals";
    }

    @Override
    public Game getGame() {
        return Game.WEISS_SCHWARZ;
    }

    @Override
    public List<NewsItem> fetchNews() throws NewsSourceException {
        List<NewsItem> items = new ArrayList<>();
        int successfulPages = 0;
        Exception firstFailure = null;
        for (Product product : products.findByGame(getGame())) {
            try {
                items.addAll(parser.parse(client.fetch(product.getDetailUrl()), product));
                successfulPages++;
            } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                log.error("Failed to fetch WS card reveals: productName={}, sourceUrl={}",
                        product.getProductName(), product.getDetailUrl(), exception);
                if (firstFailure == null) {
                    firstFailure = exception;
                }
            }
        }
        if (successfulPages == 0 && firstFailure != null) {
            throw new NewsSourceException("All discovered WS product pages failed for " + getSourceId(), firstFailure);
        }
        return items;
    }
}
