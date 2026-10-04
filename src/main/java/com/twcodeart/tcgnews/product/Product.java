package com.twcodeart.tcgnews.product;

import com.twcodeart.tcgnews.news.Game;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @Column(length = 2048)
    private String detailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Game game;

    @Column(nullable = false, length = 1000)
    private String productName;

    protected Product() {
        // Required by JPA.
    }

    public Product(Game game, String productName, String detailUrl) {
        this.game = Objects.requireNonNull(game, "game must not be null");
        this.productName = requireText(productName, "productName");
        this.detailUrl = requireText(detailUrl, "detailUrl");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public String getDetailUrl() {
        return detailUrl;
    }

    public Game getGame() {
        return game;
    }

    public String getProductName() {
        return productName;
    }
}
