package com.twcodeart.tcgnews.notification.qq;

import java.net.URI;
import java.util.Locale;
import static com.twcodeart.tcgnews.notification.qq.QqNotificationException.Reason.CONFIGURATION;

final class OfficialCardImage {
    static URI require(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getPort() != -1 && uri.getPort() != 443 || host == null
                    || !(host.equalsIgnoreCase("pbs.twimg.com") || host.equalsIgnoreCase("ws-tcg.com")
                    || host.toLowerCase(Locale.ROOT).endsWith(".ws-tcg.com"))) throw new IllegalArgumentException();
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new QqNotificationException(CONFIGURATION, "Card image must use an official WS or X image HTTPS URL");
        }
    }
    private OfficialCardImage() { }
}
