package com.twcodeart.tcgnews.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

public final class H2FileLocations {
    private H2FileLocations() { }
    public static Path databaseFile(String url) {
        if (url == null || !url.startsWith("jdbc:h2:file:")) return null;
        String base = url.substring("jdbc:h2:file:".length()).split(";", 2)[0];
        if (base.startsWith("~/")) base = System.getProperty("user.home") + base.substring(1);
        Path file = Path.of(base + ".mv.db").toAbsolutePath().normalize();
        try { return Files.exists(file) ? file.toRealPath() : file.toFile().getCanonicalFile().toPath(); }
        catch (IOException exception) { throw new IllegalArgumentException("Cannot resolve H2 database location", exception); }
    }
    public static void requireDifferent(String first, String second) {
        Path a = databaseFile(first), b = databaseFile(second);
        if (first != null && (first.equals(second) || (a != null && a.equals(b)))) {
            throw new IllegalArgumentException("Legacy, WS and OCG database locations must be different");
        }
    }
}
