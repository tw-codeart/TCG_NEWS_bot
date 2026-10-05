package com.twcodeart.tcgnews.news;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

/**
 * Hashes information-entry metadata without fetching linked pages or images.
 */
public final class InformationContentHash {

    private InformationContentHash() {
    }

    public static String generate(NewsCategory category, String sourceUrl, String title,
                                  LocalDate publishedDate, String imageUrl) {
        String metadata = String.join("\n", Game.WEISS_SCHWARZ.name(), category.name(), sourceUrl, title,
                publishedDate == null ? "" : publishedDate.toString(), imageUrl == null ? "" : imageUrl);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(metadata.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
