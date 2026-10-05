package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsCategory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class WsRssHubContentHash {

    private WsRssHubContentHash() {
    }

    static String generate(String sourceUrl, String imageUrl) {
        String identity = String.join("\n", Game.WEISS_SCHWARZ.name(), NewsCategory.CARD_REVEAL.name(),
                sourceUrl, imageUrl == null ? "" : imageUrl);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
