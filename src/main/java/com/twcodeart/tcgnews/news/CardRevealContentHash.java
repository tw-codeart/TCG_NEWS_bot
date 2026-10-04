package com.twcodeart.tcgnews.news;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

/**
 * Hashes reveal metadata only; official image bytes are never retrieved.
 */
public final class CardRevealContentHash {

    private CardRevealContentHash() {
    }

    public static String generate(String sourceUrl, LocalDate revealDate, String imageUrl) {
        String metadata = String.join("\n", Game.WEISS_SCHWARZ.name(), NewsCategory.CARD_REVEAL.name(),
                sourceUrl, revealDate.toString(), imageUrl);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(metadata.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
