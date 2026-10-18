package com.twcodeart.tcgnews.source.ocg;

import com.rometools.rome.io.FeedException;
import com.twcodeart.tcgnews.news.*;
import com.twcodeart.tcgnews.source.*;
import com.twcodeart.tcgnews.source.rsshub.*;
import com.twcodeart.tcgnews.source.ws.RssHubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.ZoneId;
import java.util.*;

@Component
@ConditionalOnProperty(prefix="rsshub",name={"enabled","ocg-enabled","ocg-jp-enabled"},havingValue="true")
public class YuGiOhJpRssHubNewsSource implements NewsSource {
    public static final String SOURCE_ID="ocg-jp-yugioh-jp-card-reveals";
    private static final Logger log=LoggerFactory.getLogger(YuGiOhJpRssHubNewsSource.class);
    private final RssHubFeedClient feed;
    private final YuGiOhJpFeedParser posts;
    private final YuGiOhJpArticleClient http;
    private final YuGiOhJpArticleParser parser;
    @Autowired public YuGiOhJpRssHubNewsSource(RssHubProperties properties,YuGiOhJpFeedParser posts,
            YuGiOhJpArticleClient http,YuGiOhJpArticleParser parser){this(new RssHubFeedClient(properties.ocgJpFeedUri()),posts,http,parser);}
    public YuGiOhJpRssHubNewsSource(RssHubFeedClient feed,YuGiOhJpFeedParser posts,YuGiOhJpArticleClient http,YuGiOhJpArticleParser parser){
        this.feed=feed;this.posts=posts;this.http=http;this.parser=parser;
    }
    @Override public String getSourceId(){return SOURCE_ID;}
    @Override public Game getGame(){return Game.YUGIOH_OCG;}
    @Override public List<NewsItem> fetchNews() throws NewsSourceException {
        List<YuGiOhJpFeedParser.Post> entries;
        try{entries=posts.parse(feed.fetch());}
        catch(InterruptedException failure){Thread.currentThread().interrupt();throw new NewsSourceException("Official JP RSSHub fetch interrupted",failure);}
        catch(IOException|FeedException|IllegalArgumentException failure){throw new NewsSourceException("Official JP RSSHub feed failed",failure);}
        var items=new ArrayList<NewsItem>();int failed=0;Exception lastFailure=null;
        var cache=new HashMap<String,YuGiOhJpArticleParser.Article>();
        for(var post:entries)for(String url:post.articleUrls()) {
            try {
                var article=cache.get(url);
                if(article==null){article=parser.parse(http.fetch(url),url);cache.put(url,article);}
                for(String image:article.imageUrls()) {
                    var item=new NewsItem(Game.YUGIOH_OCG,NewsCategory.CARD_REVEAL,post.title(),article.productName(),null,
                            post.publishedAt()==null?null:post.publishedAt().atZone(ZoneId.of("Asia/Tokyo")).toLocalDate(),
                            post.publishedAt(),image,post.sourceUrl(),PostImageContentHash.generate(Game.YUGIOH_OCG,post.sourceUrl(),image));
                    item.setArticleUrl(url);item.setArticleText(article.text());items.add(item);
                }
            }catch(IOException|IllegalArgumentException failure){failed++;lastFailure=failure;log.error("Official JP article failed; other entries continue: sourceUrl={}, articleUrl={}",post.sourceUrl(),url,failure);}
        }
        if(items.isEmpty()&&failed>0)throw new NewsSourceException("All matching official JP articles failed; check source logs",lastFailure);
        log.info("Official JP article source complete: posts={}, items={}, failedArticles={}",entries.size(),items.size(),failed);
        return List.copyOf(items);
    }
}
