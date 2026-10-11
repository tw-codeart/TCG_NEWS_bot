package com.twcodeart.tcgnews.notification.qq;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

import static com.twcodeart.tcgnews.notification.qq.QqNotificationException.Reason.CONFIGURATION;

@Component
@ConfigurationProperties(prefix = "qq")
public class QqProperties {

    private boolean enabled;
    private boolean imageForwardEnabled = true;
    private boolean ocrEnabled = true;
    private String ocrProvider = "napcat";
    private String tesseractExecutable = "";
    private String tesseractDataDirectory = "";
    public String getOcrProvider() { return ocrProvider; }
    public void setOcrProvider(String value) {
        if (!java.util.Set.of("napcat", "tesseract").contains(value))
            throw new IllegalArgumentException("qq.ocr-provider must be napcat or tesseract");
        ocrProvider = value;
    }
    public String getTesseractExecutable() { return tesseractExecutable; }
    public void setTesseractExecutable(String value) { tesseractExecutable = value; }
    public String getTesseractDataDirectory() { return tesseractDataDirectory; }
    public void setTesseractDataDirectory(String value) { tesseractDataDirectory = value; }
    private String apiBaseUrl = "http://localhost:3000";
    private String testGroupId = "";
    private String accessToken = "";
    private boolean groupSubscriptionsEnabled;
    private String commandSecret="";
    public boolean isGroupSubscriptionsEnabled(){return groupSubscriptionsEnabled;}
    public void setGroupSubscriptionsEnabled(boolean value){groupSubscriptionsEnabled=value;}
    public String getCommandSecret(){return commandSecret;}
    public void setCommandSecret(String value){commandSecret=value;}

    public boolean isImageForwardEnabled() { return imageForwardEnabled; }
    public void setImageForwardEnabled(boolean value) { imageForwardEnabled = value; }
    public boolean isOcrEnabled() { return ocrEnabled; }
    public void setOcrEnabled(boolean value) { ocrEnabled = value; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    public String getTestGroupId() { return testGroupId; }
    public void setTestGroupId(String testGroupId) { this.testGroupId = testGroupId; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public void requireConfigured() {
        requireApiConfigured();groupId();
    }

    public void requireApiConfigured() {
        if (!enabled) {
            throw new QqNotificationException(CONFIGURATION, "QQ notifications are disabled");
        }
        actionUri();
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

    public URI actionUri() { return actionUri("send_group_msg"); }

    public URI actionUri(String action) {
        if (!java.util.Set.of("send_group_msg", "send_group_forward_msg", "ocr_image", "get_login_info", "get_group_member_info").contains(action)) {
            throw new IllegalArgumentException("Unsupported QQ action");
        }
        try {
            URI base = URI.create(apiBaseUrl == null ? "" : apiBaseUrl.strip());
            if (base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null
                    || base.getFragment() != null || !("http".equalsIgnoreCase(base.getScheme())
                    || "https".equalsIgnoreCase(base.getScheme()))) {
                throw new IllegalArgumentException();
            }
            return URI.create(base.toASCIIString().replaceAll("/+$", "") + "/" + action);
        } catch (IllegalArgumentException exception) {
            throw new QqNotificationException(CONFIGURATION,
                    "qq.api-base-url must be an HTTP(S) URL without credentials, query or fragment");
        }
    }
}
