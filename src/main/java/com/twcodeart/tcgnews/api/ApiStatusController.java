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
    private final com.twcodeart.tcgnews.storage.GameStorage storage;
    private final com.twcodeart.tcgnews.notification.qq.OcrDiagnostics ocr;
    private final com.twcodeart.tcgnews.operations.CommandRetentionService retention;
    private final com.twcodeart.tcgnews.operations.RetentionProperties retentionProperties;
    private final org.springframework.beans.factory.ObjectProvider<com.twcodeart.tcgnews.source.ws.RssHubProperties> properties;
    public ApiStatusController(Environment environment,List<NewsSource> sources,
            org.springframework.beans.factory.ObjectProvider<com.twcodeart.tcgnews.source.ws.RssHubProperties> properties,
            com.twcodeart.tcgnews.storage.GameStorage storage,
            com.twcodeart.tcgnews.notification.qq.OcrDiagnostics ocr,
            com.twcodeart.tcgnews.operations.CommandRetentionService retention,
            com.twcodeart.tcgnews.operations.RetentionProperties retentionProperties) {
        this.environment=environment;this.sources=List.copyOf(sources);this.properties=properties;
        this.storage = storage;
        this.ocr=ocr;this.retention=retention;this.retentionProperties=retentionProperties;
    }
    @GetMapping("/api/status")
    public Status status() {
        boolean rss=environment.getProperty("rsshub.enabled",Boolean.class,false);
        var configured=properties.getIfAvailable();
        return new Status("post-preview-v2",Arrays.stream(Game.values()).map(Enum::name).toList(),
                sources.stream().map(NewsSource::getSourceId).toList(),
                rss && environment.getProperty("rsshub.polling-enabled",Boolean.class,false),
                (configured==null ? Duration.ofMinutes(5) : configured.getPollingInterval()).toSeconds(),
                rss && environment.getProperty("rsshub.ocg-enabled",Boolean.class,false),
                environment.getProperty("qq.enabled",Boolean.class,false),
                environment.getProperty("qq.image-forward-enabled",Boolean.class,true),
                environment.getProperty("qq.ocr-enabled",Boolean.class,true),"file-logging-v1",
                storage.isSeparated() ? "SEPARATED" : "SHARED", storage.isReady(),"compact-forward-v1",
                com.twcodeart.tcgnews.notification.qq.QqNotificationSender.MAX_FORWARD_ITEMS,
                environment.getProperty("qq.ocr-provider", "napcat"),ocr.snapshot(),
                retentionProperties.isEnabled(), retentionProperties.getCommandDays(), retention.snapshot(),
                rss && environment.getProperty("rsshub.ocg-enabled",Boolean.class,false)
                        && environment.getProperty("rsshub.ocg-jp-enabled",Boolean.class,false));
    }
    public record Status(String apiRevision,List<String> games,List<String> sources,boolean pollingEnabled,
                         long pollingIntervalSeconds,boolean ocgEnabled,boolean qqEnabled,
                         boolean imageForwardEnabled,boolean ocrEnabled,String operationsRevision,
                         String storageMode,boolean storageReady,String forwardRevision,int forwardItemLimit,
                         String ocrProvider,com.twcodeart.tcgnews.notification.qq.OcrDiagnostics.Snapshot ocr,
                         boolean retentionEnabled,int commandRetentionDays,
                         com.twcodeart.tcgnews.operations.CommandRetentionService.Snapshot retention,boolean ocgJpEnabled) { }
}
