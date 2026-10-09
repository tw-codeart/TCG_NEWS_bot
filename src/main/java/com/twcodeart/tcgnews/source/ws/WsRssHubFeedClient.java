package com.twcodeart.tcgnews.source.ws;
import com.twcodeart.tcgnews.source.rsshub.RssHubFeedClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
@Component
@ConditionalOnProperty(prefix = "rsshub", name = "enabled", havingValue = "true")
public class WsRssHubFeedClient {
    private final RssHubFeedClient client;
    @Autowired
    public WsRssHubFeedClient(RssHubProperties properties) { this.client = new RssHubFeedClient(properties.feedUri()); }
    WsRssHubFeedClient(URI uri, HttpClient http) { this.client = new RssHubFeedClient(uri, http); }
    public byte[] fetch() throws IOException, InterruptedException { return client.fetch(); }
}
