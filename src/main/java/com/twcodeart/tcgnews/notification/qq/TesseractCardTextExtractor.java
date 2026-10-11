package com.twcodeart.tcgnews.notification.qq;

import com.twcodeart.tcgnews.notification.ocr.CardTextExtractor;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import static com.twcodeart.tcgnews.notification.qq.QqNotificationException.Reason.*;

/** Optional local Japanese OCR. Original bytes are temporary; no resizing, crop or watermark changes. */
final class TesseractCardTextExtractor implements CardTextExtractor {
    static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private final QqProperties properties;
    private final ImageLoader loader;
    private final Runner runner;
    interface ImageLoader { byte[] load(URI uri) throws IOException, InterruptedException; }
    interface Runner { String run(Path image, Path directory, Path executable, Path data) throws IOException, InterruptedException; }

    TesseractCardTextExtractor(QqProperties properties) {
        this(properties, TesseractCardTextExtractor::download, TesseractCardTextExtractor::recognize);
    }
    TesseractCardTextExtractor(QqProperties properties, ImageLoader loader, Runner runner) {
        this.properties = properties; this.loader = loader; this.runner = runner;
    }

    @Override public String extract(String imageUrl) {
        URI uri = OfficialCardImage.require(imageUrl);
        Path directory = null;
        try {
            Path executable = Path.of(properties.getTesseractExecutable());
            Path data = Path.of(properties.getTesseractDataDirectory());
            if (!executable.isAbsolute() || !Files.isRegularFile(executable) || !data.isAbsolute()
                    || !Files.isRegularFile(data.resolve("jpn.traineddata")))
                throw new QqNotificationException(CONFIGURATION, "Local OCR requires an absolute executable and jpn.traineddata directory");
            byte[] bytes = loader.load(uri);
            if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES)
                throw new QqNotificationException(API_FAILURE, "OCR image is empty or exceeds 10 MiB");
            directory = Files.createTempDirectory("tcg-ocr-");
            Path image = directory.resolve("original-image");
            Files.write(image, bytes);
            return runner.run(image, directory, executable, data).strip();
        } catch (HttpTimeoutException exception) {
            throw new QqNotificationException(TIMEOUT, "OCR image request timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new QqNotificationException(TIMEOUT, "Local OCR interrupted");
        } catch (IOException | InvalidPathException exception) {
            throw new QqNotificationException(API_FAILURE, "Local OCR input/process failed; check executable and language data");
        } finally {
            if (directory != null) {
                try {
                    List<Path> children;
                    try (var files = Files.list(directory)) { children = files.toList(); }
                    for (Path file : children) Files.deleteIfExists(file);
                    Files.deleteIfExists(directory);
                } catch (IOException exception) {
                    org.slf4j.LoggerFactory.getLogger(TesseractCardTextExtractor.class)
                            .warn("OCR temporary cleanup failed; manual OS temporary-file cleanup may be needed");
                }
            }
        }
    }

    private static byte[] download(URI uri) throws IOException, InterruptedException {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build();
        // A bounded byte-array subscriber keeps the entire response deadline active, including its body.
        var future = http.sendAsync(request, info -> new LimitedImageSubscriber(MAX_IMAGE_BYTES));
        try {
            var response = future.get(15, TimeUnit.SECONDS);
            if (response.statusCode() != 200)
                throw new QqNotificationException(API_FAILURE, "OCR image HTTP request did not return 200");
            return response.body();
        } catch (TimeoutException exception) {
            throw new HttpTimeoutException("OCR image deadline exceeded");
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof IOException failure) throw failure;
            throw new IOException("OCR image request failed");
        } finally { if (!future.isDone()) future.cancel(true); }
    }

    private static String recognize(Path image, Path directory, Path executable, Path data) throws IOException, InterruptedException {
        Path output = directory.resolve("result");
        Process process = new ProcessBuilder(List.of(executable.toString(), image.toString(), output.toString(),
                "--tessdata-dir", data.toString(), "-l", "jpn", "--oem", "1", "--psm", "11"))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if (!process.waitFor(20, TimeUnit.SECONDS))
                throw new QqNotificationException(TIMEOUT, "Local OCR process timed out");
            if (process.exitValue() != 0) throw new QqNotificationException(API_FAILURE, "Local OCR process exited unsuccessfully");
            Path text = directory.resolve("result.txt");
            if (!Files.isRegularFile(text) || Files.size(text) > 65536)
                throw new QqNotificationException(API_FAILURE, "Local OCR returned missing or oversized text");
            return Files.readString(text, StandardCharsets.UTF_8);
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        }
    }
}
