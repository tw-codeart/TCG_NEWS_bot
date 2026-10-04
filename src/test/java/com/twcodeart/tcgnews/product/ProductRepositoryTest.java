package com.twcodeart.tcgnews.product;

import com.twcodeart.tcgnews.news.Game;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ProductRepositoryTest {

    @Autowired
    private ProductRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesNamesAndUrlsAndUsesDetailUrlAsCatalogIdentity() {
        String detailUrl = "https://ws-tcg.com/products/test/";
        repository.saveAndFlush(new Product(Game.WEISS_SCHWARZ, "Original name", detailUrl));
        entityManager.clear();

        Product reloaded = repository.findById(detailUrl).orElseThrow();
        assertThat(reloaded.getGame()).isEqualTo(Game.WEISS_SCHWARZ);
        assertThat(reloaded.getProductName()).isEqualTo("Original name");
        assertThat(reloaded.getDetailUrl()).isEqualTo(detailUrl);

        repository.saveAndFlush(new Product(Game.WEISS_SCHWARZ, "Updated name", detailUrl));
        entityManager.clear();

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById(detailUrl).orElseThrow().getProductName()).isEqualTo("Updated name");
    }
}
