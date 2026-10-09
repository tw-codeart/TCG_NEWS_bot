package com.twcodeart.tcgnews.source.ws;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "rsshub", name = "enabled", havingValue = "true")
@ConfigurationProperties(prefix = "rsshub")
public class RssHubProperties {

    private String baseUrl;
    private boolean ocgEnabled;
    private String ocgRoute = "/twitter/user/YuGiOh_OCG_INFO";

    public boolean isOcgEnabled() { return ocgEnabled; }
    public void setOcgEnabled(boolean value) { ocgEnabled = value; }
    public String getOcgRoute() { return ocgRoute; }
    public void setOcgRoute(String value) { ocgRoute = value; }
    public URI ocgFeedUri() { return feedUri(ocgRoute); }
    private String wsRoute = "/twitter/user/wstcg";
    private int notificationBatchLimit = 3;
    private Duration pollingInterval = Duration.ofMinutes(5);

    public Duration getPollingInterval() {
        return pollingInterval;
    }

    public void setPollingInterval(Duration pollingInterval) {
        if (pollingInterval == null || pollingInterval.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("rsshub.polling-interval must be at least 1 minute");
        }
        this.pollingInterval = pollingInterval;
    }

    public int getNotificationBatchLimit() {
        return notificationBatchLimit;
    }

    public void setNotificationBatchLimit(int notificationBatchLimit) {
        if (notificationBatchLimit < 1 || notificationBatchLimit > 5) {
            throw new IllegalArgumentException("rsshub.notification-batch-limit must be between 1 and 5");
        }
        this.notificationBatchLimit = notificationBatchLimit;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getWsRoute() {
        return wsRoute;
    }

    public void setWsRoute(String wsRoute) {
        this.wsRoute = wsRoute;
    }

    public URI feedUri() { return feedUri(wsRoute); }

    private URI feedUri(String routePath) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("rsshub.base-url is required when RSSHub is enabled");
        }
        URI base = URI.create(baseUrl.strip());
        if (!base.isAbsolute() || base.getHost() == null
                || !("https".equalsIgnoreCase(base.getScheme()) || "http".equalsIgnoreCase(base.getScheme()))
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalArgumentException("rsshub.base-url must be an HTTP(S) URL without credentials, query or fragment");
        }
        if (routePath == null || !routePath.startsWith("/") || routePath.startsWith("//")) {
            throw new IllegalArgumentException("rsshub.ws-route must start with a single slash");
        }
        URI route = URI.create(routePath);
        if (route.getFragment() != null) {
            throw new IllegalArgumentException("rsshub.ws-route must not contain a fragment");
        }
        String prefix = base.toASCIIString().replaceAll("/+$", "");
        return URI.create(prefix + routePath).normalize();
    }
}
