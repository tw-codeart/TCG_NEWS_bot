package com.twcodeart.tcgnews.notification.qq;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

import static com.twcodeart.tcgnews.notification.qq.QqNotificationException.Reason.CONFIGURATION;

@Component
@ConfigurationProperties(prefix = "qq")
public class QqProperties {

    private boolean enabled;
    private String apiBaseUrl = "http://localhost:3000";
    private String testGroupId = "";
    private String accessToken = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    public String getTestGroupId() { return testGroupId; }
    public void setTestGroupId(String testGroupId) { this.testGroupId = testGroupId; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public void requireConfigured() {
        if (!enabled) {
            throw new QqNotificationException(CONFIGURATION, "QQ notifications are disabled");
        }
        actionUri();
        groupId();
        if (accessToken != null && accessToken.chars().anyMatch(character -> character < 32 || character > 126)) {
            throw new QqNotificationException(CONFIGURATION, "Invalid QQ access-token header value");
        }
    }

    public long groupId() {
        try {
            if (testGroupId == null || !testGroupId.matches("[1-9][0-9]*")) {
                throw new IllegalArgumentException();
            }
            return Long.parseLong(testGroupId);
        } catch (IllegalArgumentException exception) {
            throw new QqNotificationException(CONFIGURATION, "qq.test-group-id must be a positive numeric group ID");
        }
    }

    public URI actionUri() {
        try {
            URI base = URI.create(apiBaseUrl == null ? "" : apiBaseUrl.strip());
            if (base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null
                    || base.getFragment() != null || !("http".equalsIgnoreCase(base.getScheme())
                    || "https".equalsIgnoreCase(base.getScheme()))) {
                throw new IllegalArgumentException();
            }
            return URI.create(base.toASCIIString().replaceAll("/+$", "") + "/send_group_msg");
        } catch (IllegalArgumentException exception) {
            throw new QqNotificationException(CONFIGURATION,
                    "qq.api-base-url must be an HTTP(S) URL without credentials, query or fragment");
        }
    }
}
