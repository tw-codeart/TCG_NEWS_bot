package com.twcodeart.tcgnews.source.ocg;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Normal isolated Chromium rendering; never uses the user's profile or solves challenges. */
public class ArticleBrowserReader {
    interface Runner { void run(List<String> arguments,Path output) throws IOException; }
    private final Runner runner;
    public ArticleBrowserReader(){this(ArticleBrowserReader::execute);}
    ArticleBrowserReader(Runner runner){this.runner=runner;}
    public String read(String executable,String articleUrl) throws IOException {
        String url=YuGiOhJpUrls.article(articleUrl);
        Path browser=Path.of(executable==null?"":executable);
        if(!browser.isAbsolute()||!Files.isRegularFile(browser)
                ||!Set.of("msedge.exe","chrome.exe").contains(browser.getFileName().toString().toLowerCase(Locale.ROOT)))
            throw new IOException("Configure an installed Edge/Chrome executable for article rendering");
        Path tempRoot=Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        Path temporary=Files.createTempDirectory(tempRoot,"tcg-article-browser-");
        try {
            Path output=temporary.resolve("article.html");
            runner.run(List.of(browser.toString(),"--headless","--disable-gpu","--no-first-run","--no-default-browser-check",
                    "--user-data-dir="+temporary.resolve("profile"),"--dump-dom",url),output);
            if(!Files.isRegularFile(output)||Files.size(output)>2*1024*1024)throw new IOException("Browser returned missing/oversized article HTML");
            String html=Files.readString(output,StandardCharsets.UTF_8);
            var document=org.jsoup.Jsoup.parse(html);
            if(document.selectFirst("article [data-news-body]")==null)
                throw new IOException("Browser did not return the article; a challenge or browser error requires manual investigation (chars="
                        +html.length()+", title="+document.title().replaceAll("[\\r\\n]"," ").substring(0,Math.min(80,document.title().length()))+")");
            return html;
        } finally {
            // Never follow profile links, and verify the exact freshly created cleanup boundary.
            if(!temporary.toAbsolutePath().normalize().getParent().equals(tempRoot)
                    ||!temporary.getFileName().toString().startsWith("tcg-article-browser-"))throw new IOException("Unsafe browser cleanup boundary");
            try {
                List<Path> children;
                try(var files=Files.walk(temporary)){children=files.sorted(Comparator.reverseOrder()).toList();}
                for(Path path:children)Files.deleteIfExists(path);
            }catch(IOException failure){org.slf4j.LoggerFactory.getLogger(ArticleBrowserReader.class).warn("Temporary browser profile cleanup failed; OS temporary-file cleanup may be needed");}
        }
    }
    private static void execute(List<String> arguments,Path output) throws IOException {
        var builder=new ProcessBuilder(arguments).redirectOutput(output.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);
        for(String secret:List.of("QQ_ACCESS_TOKEN","QQ_COMMAND_SECRET","TWITTER_AUTH_TOKEN"))builder.environment().remove(secret);
        var process=builder.start();
        try {
            if(!process.waitFor(45,TimeUnit.SECONDS))throw new IOException("Article browser timed out");
            if(process.exitValue()!=0)throw new IOException("Article browser exited unsuccessfully");
        }catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IOException("Article browser interrupted",failure);}
        finally {
            if(process.isAlive()) {
                var children=process.descendants().toList();
                for(var child:children)child.destroyForcibly();process.destroyForcibly();
                try{process.waitFor(5,TimeUnit.SECONDS);}catch(InterruptedException failure){Thread.currentThread().interrupt();}
            }
        }
    }
}
