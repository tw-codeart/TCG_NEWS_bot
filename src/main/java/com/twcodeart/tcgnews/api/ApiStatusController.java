package com.twcodeart.tcgnews.api;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.source.NewsSource;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/** Read-only operational flags; never exposes credentials, group IDs or source URL settings. */
@RestController
public class ApiStatusController {
    private final Environment environment;
    private final List<NewsSource> sources;
    private final org.springframework.beans.factory.ObjectProvider<com.twcodeart.tcgnews.source.ws.RssHubProperties> properties;
    public ApiStatusController(Environment environment,List<NewsSource> sources,
            org.springframework.beans.factory.ObjectProvider<com.twcodeart.tcgnews.source.ws.RssHubProperties> properties) {
        this.environment=environment;this.sources=List.copyOf(sources);this.properties=properties;
    }
    @GetMapping("/api/status")
    public Status status() {
        boolean rss=environment.getProperty("rsshub.enabled",Boolean.class,false);
        var configured=properties.getIfAvailable();
        return new Status("post-preview-v1",Arrays.stream(Game.values()).map(Enum::name).toList(),
                sources.stream().map(NewsSource::getSourceId).toList(),
                rss && environment.getProperty("rsshub.polling-enabled",Boolean.class,false),
                (configured==null ? Duration.ofMinutes(5) : configured.getPollingInterval()).toSeconds(),
                rss && environment.getProperty("rsshub.ocg-enabled",Boolean.class,false),
                environment.getProperty("qq.enabled",Boolean.class,false),
                environment.getProperty("qq.image-forward-enabled",Boolean.class,true),
                environment.getProperty("qq.ocr-enabled",Boolean.class,true));
    }
    public record Status(String apiRevision,List<String> games,List<String> sources,boolean pollingEnabled,
                         long pollingIntervalSeconds,boolean ocgEnabled,boolean qqEnabled,
                         boolean imageForwardEnabled,boolean ocrEnabled) { }
}
