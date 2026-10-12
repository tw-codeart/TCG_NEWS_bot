package com.twcodeart.tcgnews.source.ocg;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class YuGiOhJpArticleClient {
    private static final int MAX_BYTES=2*1024*1024;
    private final com.twcodeart.tcgnews.source.ws.RssHubProperties properties;
    public YuGiOhJpArticleClient(){this.properties=null;}
    @org.springframework.beans.factory.annotation.Autowired
    public YuGiOhJpArticleClient(org.springframework.beans.factory.ObjectProvider<com.twcodeart.tcgnews.source.ws.RssHubProperties> properties){this.properties=properties.getIfAvailable();}
    public String fetch(String articleUrl) throws IOException {
        String url=YuGiOhJpUrls.article(articleUrl);
        if(properties!=null&&properties.isArticleBrowserEnabled())
            return new ArticleBrowserReader().read(properties.getArticleBrowserExecutable(),url);
        var response=Jsoup.connect(url).userAgent("TCG-News-Bot/0.1 (+https://github.com/tw-codeart/TCG_NEWS_bot)")
                .timeout(15000).maxBodySize(MAX_BYTES).followRedirects(false).execute();
        if(response.statusCode()!=200)throw new IOException("Official article returned HTTP "+response.statusCode());
        if(response.bodyAsBytes().length>=MAX_BYTES)throw new IOException("Official article exceeds 2 MiB HTML limit");
        return response.body();
    }
}
