package com.twcodeart.tcgnews.news;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "news_items")
public class NewsItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Game game;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NewsCategory category;

    @Column(length = 1000)
    private String title;

    @Column(length = 1000)
    private String productName;

    private LocalDate revealDate;

    private LocalDate publishedDate;

    private Instant publishedAt;

    @Column(length = 2048)
    private String imageUrl;

    @Column(nullable = false, length = 2048)
    private String sourceUrl;

    @Column(name = "content_hash", nullable = false, length = 64, unique = true)
    private String contentHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected NewsItem() {
        // Required by JPA.
    }

    public NewsItem(Game game, NewsCategory category, String title, String productName,
                    LocalDate revealDate, String imageUrl, String sourceUrl, String contentHash) {
        this(game, category, title, productName, revealDate, null, imageUrl, sourceUrl, contentHash);
    }

    public NewsItem(Game game, NewsCategory category, String title, String productName,
                    LocalDate revealDate, LocalDate publishedDate, String imageUrl,
                    String sourceUrl, String contentHash) {
        this(game, category, title, productName, revealDate, publishedDate, null, imageUrl, sourceUrl, contentHash);
    }

    public NewsItem(Game game, NewsCategory category, String title, String productName,
                    LocalDate revealDate, LocalDate publishedDate, Instant publishedAt,
                    String imageUrl, String sourceUrl, String contentHash) {
        this.game = Objects.requireNonNull(game, "game must not be null");
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.title = title;
        this.productName = productName;
        this.revealDate = revealDate;
        this.publishedDate = publishedDate;
        this.publishedAt = publishedAt;
        this.imageUrl = imageUrl;
        this.sourceUrl = requireText(sourceUrl, "sourceUrl");
        this.contentHash = requireText(contentHash, "contentHash");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    @PrePersist
    private void initializeCreatedAt() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public NewsCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getProductName() {
        return productName;
    }

    public LocalDate getRevealDate() {
        return revealDate;
    }

    public LocalDate getPublishedDate() {
        return publishedDate;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
