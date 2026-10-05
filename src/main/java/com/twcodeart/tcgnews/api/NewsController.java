package com.twcodeart.tcgnews.api;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsFetchService;
import com.twcodeart.tcgnews.news.NewsFetchSummary;
import com.twcodeart.tcgnews.news.NewsItem;
import com.twcodeart.tcgnews.news.NewsQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class NewsController {

    private final NewsQueryService query;
    private final NewsFetchService fetch;

    public NewsController(NewsQueryService query, NewsFetchService fetch) {
        this.query = query;
        this.fetch = fetch;
    }

    @GetMapping("/news")
    public List<NewsItem> getNews(@RequestParam(name = "game", required = false) Game game) {
        return query.findNews(game);
    }

    @PostMapping("/fetch")
    public ResponseEntity<NewsFetchSummary> fetchNews() {
        NewsFetchSummary summary = fetch.fetchAll();
        HttpStatus status = summary.allSourcesFailed() ? HttpStatus.BAD_GATEWAY : HttpStatus.OK;
        return ResponseEntity.status(status).body(summary);
    }
}
