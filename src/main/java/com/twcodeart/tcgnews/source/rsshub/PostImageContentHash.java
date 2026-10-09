package com.twcodeart.tcgnews.source.rsshub;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsCategory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class PostImageContentHash {

    private PostImageContentHash() {
    }

    public static String generate(Game game, String sourceUrl, String imageUrl) {
        String identity = String.join("\n", game.name(), NewsCategory.CARD_REVEAL.name(),
                sourceUrl, imageUrl == null ? "" : imageUrl);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
