package com.twcodeart.tcgnews.notification.qq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class TesseractCardTextExtractorTest {
    @TempDir Path root;
    static final String IMAGE = "https://pbs.twimg.com/media/synthetic.jpg";

    QqProperties configured() throws Exception {
        var settings = new QqProperties(); settings.setOcrProvider("tesseract");
        settings.setTesseractExecutable(Files.createFile(root.resolve("tesseract.exe")).toString());
        var data = Files.createDirectory(root.resolve("Japanese language")); Files.createFile(data.resolve("jpn.traineddata"));
        settings.setTesseractDataDirectory(data.toString()); return settings;
    }

    @Test void preservesOriginalBytesAndJapaneseTextThenRemovesTemporaryFiles() throws Exception {
        byte[] original = "synthetic image bytes".getBytes(StandardCharsets.UTF_8);
        var temporary = new AtomicReference<Path>();
        var extractor = new TesseractCardTextExtractor(configured(), uri -> original, (image, directory, exe, data) -> {
            temporary.set(directory);
            assertThat(Files.readAllBytes(image)).isEqualTo(original);
            assertThat(data.resolve("jpn.traineddata")).exists();
            return " 【自】このカードが舞台に置かれた時。\n ";
        });
        assertThat(extractor.extract(IMAGE)).isEqualTo("【自】このカードが舞台に置かれた時。");
        assertThat(temporary.get()).doesNotExist();
    }

    @Test void processFailureCleansOriginalImageAndReturnsSafeFailure() throws Exception {
        var temporary = new AtomicReference<Path>();
        var extractor = new TesseractCardTextExtractor(configured(), uri -> new byte[]{1}, (image, directory, exe, data) -> {
            temporary.set(directory); Files.writeString(directory.resolve("result.txt"), "partial");
            throw new QqNotificationException(QqNotificationException.Reason.TIMEOUT, "Local OCR process timed out");
        });
        assertThatThrownBy(() -> extractor.extract(IMAGE)).hasMessage("Local OCR process timed out");
        assertThat(temporary.get()).doesNotExist();
    }

    @Test void missingJapaneseLanguageDataMakesNoImageRequest() throws Exception {
        var settings = configured(); Files.delete(Path.of(settings.getTesseractDataDirectory()).resolve("jpn.traineddata"));
        var extractor = new TesseractCardTextExtractor(settings, uri -> { throw new AssertionError("No HTTP allowed"); },
                (i,d,e,t) -> { throw new AssertionError("No process allowed"); });
        assertThatThrownBy(() -> extractor.extract(IMAGE)).hasMessageContaining("jpn.traineddata");
    }

    @Test void rejectsOversizedImageAndUntrustedUrlsBeforeRunningOcr() throws Exception {
        var extractor = new TesseractCardTextExtractor(configured(), uri -> new byte[TesseractCardTextExtractor.MAX_IMAGE_BYTES+1],
                (i,d,e,t) -> { throw new AssertionError("No process allowed"); });
        assertThatThrownBy(() -> extractor.extract(IMAGE)).hasMessageContaining("10 MiB");
        for(String value: new String[]{"http://pbs.twimg.com/a", "https://localhost/a", "https://pbs.twimg.com:444/a"})
            assertThatThrownBy(() -> extractor.extract(value)).hasMessageContaining("official WS or X");
    }

    @Test void interruptedOcrPreservesInterruptFlagAndCleansItsFiles() throws Exception {
        var temporary = new AtomicReference<Path>();
        var extractor = new TesseractCardTextExtractor(configured(), uri -> new byte[]{1}, (i,d,e,t) -> {
            temporary.set(d); throw new InterruptedException();
        });
        try {
            assertThatThrownBy(() -> extractor.extract(IMAGE)).hasMessage("Local OCR interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(temporary.get()).doesNotExist();
        } finally { Thread.interrupted(); }
    }

    @Test void boundedSubscriberRejectsExcessBytesAndCancelsRequest() {
        var subscriber = new LimitedImageSubscriber(3);
        var subscription = org.mockito.Mockito.mock(java.util.concurrent.Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        subscriber.onNext(java.util.List.of(java.nio.ByteBuffer.wrap(new byte[]{1,2,3,4})));
        assertThat(subscriber.getBody().toCompletableFuture()).isCompletedExceptionally();
        org.mockito.Mockito.verify(subscription).cancel();
    }
}
