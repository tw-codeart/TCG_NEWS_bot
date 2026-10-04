package com.twcodeart.tcgnews.news;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {

    boolean existsByContentHash(String contentHash);
}
