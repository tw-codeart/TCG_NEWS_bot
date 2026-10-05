package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.product.Product;
import com.twcodeart.tcgnews.product.ProductRepository;
import com.twcodeart.tcgnews.source.NewsSourceException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WsCardRevealSourceTest {

    @Mock
    private ProductRepository products;

    @Mock
    private WsProductPageClient client;

    @Mock
    private WsProductDiscoveryService discovery;

    @Test
    void continuesWithOtherDiscoveredProductUrlsAfterAnHttpFailure() throws Exception {
        Product failed = product("failed");
        Product valid = product("valid");
        when(products.findByGame(Game.WEISS_SCHWARZ)).thenReturn(List.of(failed, valid));
        when(client.fetch(failed.getDetailUrl())).thenThrow(new IOException("Connection timed out"));
        try (InputStream fixture = Objects.requireNonNull(
                getClass().getResourceAsStream("/fixtures/ws-card-reveal.html"))) {
            when(client.fetch(valid.getDetailUrl())).thenReturn(Jsoup.parse(fixture, "UTF-8", valid.getDetailUrl()));
        }

        var source = new WsCardRevealSource(products, client, new WsCardRevealsParser(), discovery);
        assertThat(source.fetchNews()).singleElement().satisfies(item ->
                assertThat(item.getSourceUrl()).isEqualTo(valid.getDetailUrl()));
        var order = inOrder(discovery, products);
        order.verify(discovery).discoverProducts();
        order.verify(products).findByGame(Game.WEISS_SCHWARZ);
    }

    @Test
    void reportsFailureWithItsCauseWhenEveryProductPageFails() throws IOException {
        Product product = product("failed");
        IOException failure = new IOException("Connection timed out");
        when(products.findByGame(Game.WEISS_SCHWARZ)).thenReturn(List.of(product));
        when(client.fetch(product.getDetailUrl())).thenThrow(failure);

        var source = new WsCardRevealSource(products, client, new WsCardRevealsParser(), discovery);
        assertThatThrownBy(source::fetchNews).isInstanceOf(NewsSourceException.class)
                .hasMessageContaining("All discovered WS product pages failed")
                .hasCause(failure);
    }

    @Test
    void returnsEmptyWithoutDetailRequestsWhenDiscoveryLeavesNoProducts() throws NewsSourceException {
        when(products.findByGame(Game.WEISS_SCHWARZ)).thenReturn(List.of());
        var source = new WsCardRevealSource(products, client, new WsCardRevealsParser(), discovery);

        assertThat(source.fetchNews()).isEmpty();
        verify(discovery).discoverProducts();
        verifyNoInteractions(client);
    }

    @Test
    void reportsCatalogDiscoveryFailureWithoutRequestingDetailPages() throws NewsSourceException {
        NewsSourceException failure = new NewsSourceException("Discovery failed", new IOException("Timed out"));
        when(discovery.discoverProducts()).thenThrow(failure);
        var source = new WsCardRevealSource(products, client, new WsCardRevealsParser(), discovery);

        assertThatThrownBy(source::fetchNews).isSameAs(failure);
        verifyNoInteractions(products, client);
    }

    private Product product(String slug) {
        return new Product(Game.WEISS_SCHWARZ, "Test " + slug, "https://ws-tcg.com/products/" + slug + "/");
    }
}
