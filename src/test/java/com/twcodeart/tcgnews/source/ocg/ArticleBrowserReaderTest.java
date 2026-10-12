package com.twcodeart.tcgnews.source.ocg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class ArticleBrowserReaderTest {
    @TempDir Path root;
    @Test void isolatedRenderingPreservesArticleAndRemovesItsTemporaryProfile() throws Exception {
        Path browser=Files.createFile(root.resolve("chrome.exe"));var temp=new AtomicReference<Path>();
        String fixture=YuGiOhJpParsersTest.html();
        var reader=new ArticleBrowserReader((arguments,output)->{
            temp.set(output.getParent());
            assertThat(arguments).contains("--headless","--dump-dom","https://yu-gi-oh.jp/news/synthetic-article/");
            assertThat(arguments).noneMatch(arg->arg.contains("ignore-certificate")||arg.contains("no-sandbox")||arg.contains("remote-debugging"));
            assertThat(arguments).anyMatch(arg->arg.startsWith("--user-data-dir="+output.getParent()));
            Files.writeString(output,fixture);
        });
        String html=reader.read(browser.toString(),YuGiOhJpParsersTest.URL);
        assertThat(new YuGiOhJpArticleParser().parse(html,YuGiOhJpParsersTest.URL).imageUrls()).hasSize(5);
        assertThat(temp.get()).doesNotExist();
    }
    @Test void verificationPagesAndProcessFailureAreReportedAndCleaned() throws Exception {
        Path browser=Files.createFile(root.resolve("msedge.exe"));var temp=new AtomicReference<Path>();
        var blocked=new ArticleBrowserReader((args,output)->{temp.set(output.getParent());Files.writeString(output,"<html><title>Verification required</title></html>");});
        assertThatThrownBy(()->blocked.read(browser.toString(),YuGiOhJpParsersTest.URL)).hasMessageContaining("challenge");
        assertThat(temp.get()).doesNotExist();
        var failed=new ArticleBrowserReader((args,output)->{temp.set(output.getParent());throw new java.io.IOException("Synthetic timeout");});
        assertThatThrownBy(()->failed.read(browser.toString(),YuGiOhJpParsersTest.URL)).hasMessage("Synthetic timeout");
        assertThat(temp.get()).doesNotExist();
    }
    @Test void unsupportedExecutableAndUrlNeverStartAProcess() {
        var reader=new ArticleBrowserReader((args,output)->{throw new AssertionError("No process expected");});
        assertThatThrownBy(()->reader.read("unknown.exe",YuGiOhJpParsersTest.URL)).hasMessageContaining("installed Edge/Chrome");
        assertThatThrownBy(()->reader.read("unknown.exe","https://attacker.example/news/a/")).isInstanceOf(IllegalArgumentException.class);
    }
}
