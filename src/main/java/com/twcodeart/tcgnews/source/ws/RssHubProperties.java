package com.twcodeart.tcgnews.source.ws;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
@ConditionalOnProperty(prefix = "rsshub", name = "enabled", havingValue = "true")
@ConfigurationProperties(prefix = "rsshub")
public class RssHubProperties {

    private String baseUrl;
    private String wsRoute = "/twitter/user/wstcg";
    private int notificationBatchLimit = 3;

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

    public URI feedUri() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("rsshub.base-url is required when RSSHub is enabled");
        }
        URI base = URI.create(baseUrl.strip());
        if (!base.isAbsolute() || base.getHost() == null
                || !("https".equalsIgnoreCase(base.getScheme()) || "http".equalsIgnoreCase(base.getScheme()))
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalArgumentException("rsshub.base-url must be an HTTP(S) URL without credentials, query or fragment");
        }
        if (wsRoute == null || !wsRoute.startsWith("/") || wsRoute.startsWith("//")) {
            throw new IllegalArgumentException("rsshub.ws-route must start with a single slash");
        }
        URI route = URI.create(wsRoute);
        if (route.getFragment() != null) {
            throw new IllegalArgumentException("rsshub.ws-route must not contain a fragment");
        }
        String prefix = base.toASCIIString().replaceAll("/+$", "");
        return URI.create(prefix + wsRoute).normalize();
    }
}
