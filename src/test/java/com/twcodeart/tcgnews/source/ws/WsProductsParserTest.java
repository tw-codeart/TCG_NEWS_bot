package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.product.Product;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WsProductsParserTest {

    private final WsProductsParser parser = new WsProductsParser();

    @Test
    void discoversCarouselAndListProductsAndSkipsInvalidEntries() throws IOException {
        try (InputStream fixture = getClass().getResourceAsStream("/fixtures/ws-products.html")) {
            assertThat(fixture).isNotNull();
            List<Product> products = parser.parse(Jsoup.parse(fixture, "UTF-8",
                    WsProductDiscoveryService.PRODUCTS_URL));

            assertThat(products).extracting(Product::getProductName)
                    .containsExactly("将来の商品 Vol.2", "最近の商品", "将来の商品 Vol.2", "将来の商品 Vol.2");
            assertThat(products).extracting(Product::getDetailUrl).containsExactly(
                    "https://ws-tcg.com/products/future/",
                    "https://ws-tcg.com/products/recent/",
                    "https://ws-tcg.com/products/future/",
                    "https://ws-tcg.com/products/trial/");
            assertThat(products).allSatisfy(product ->
                    assertThat(product.getGame()).isEqualTo(Game.WEISS_SCHWARZ));
        }
    }

    @Test
    void reportsChangedPageStructureInsteadOfReturningAnEmptySuccess() {
        assertThatThrownBy(() -> parser.parse(Jsoup.parse("<html><body>Unexpected page</body></html>")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing expected product sections");
    }

    @Test
    void reportsASectionWhereEveryProductEntryIsInvalid() {
        String html = """
                <ul class="products__lists">
                    <li><a class="products__link" href="/products/invalid/">
                        <p class="products__name"></p>
                    </a></li>
                </ul>
                """;
        assertThatThrownBy(() -> parser.parse(Jsoup.parse(html, WsProductDiscoveryService.PRODUCTS_URL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no valid product entries");
    }
}
