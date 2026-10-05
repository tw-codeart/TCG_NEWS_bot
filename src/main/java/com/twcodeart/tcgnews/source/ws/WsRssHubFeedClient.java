package com.twcodeart.tcgnews.source.ws;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "rsshub", name = "enabled", havingValue = "true")
public class WsRssHubFeedClient {

    private final URI feedUri;
    private final HttpClient client;

    @Autowired
    public WsRssHubFeedClient(RssHubProperties properties) {
        this(properties.feedUri(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    WsRssHubFeedClient(URI feedUri, HttpClient client) {
        this.feedUri = feedUri;
        this.client = client;
    }

    public byte[] fetch() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(feedUri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "TCG-News-Bot/0.1 (+https://github.com/tw-codeart/TCG_NEWS_bot)")
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                .GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("RSSHub returned HTTP " + response.statusCode());
        }
        return response.body();
    }
}
