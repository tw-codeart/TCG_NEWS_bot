package com.twcodeart.tcgnews.source.ocg;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Selectors verified against the site's article DOM on 2026-10-12. */
@Component
public class YuGiOhJpArticleParser {
    private static final Logger log=LoggerFactory.getLogger(YuGiOhJpArticleParser.class);
    public Article parse(String html,String articleUrl) {
        String url=YuGiOhJpUrls.article(articleUrl);
        var document=Jsoup.parse(html,url);
        var article=document.selectFirst("article");
        if(article==null)throw new IllegalArgumentException("Official article element missing");
        boolean ocg=article.select("a[href]").stream().anyMatch(link -> {
            try {var uri=java.net.URI.create(link.absUrl("href"));return "yu-gi-oh.jp".equalsIgnoreCase(uri.getHost())
                    && "/news/search/".equals(uri.getPath()) && "tag=ocg".equalsIgnoreCase(uri.getQuery());}
            catch(IllegalArgumentException failure){return false;}
        });
        if(!ocg)return new Article(null,null,List.of());
        var heading=article.selectFirst("header h1");
        if(heading==null||heading.text().isBlank())throw new IllegalArgumentException("Official article heading missing");
        var body=article.selectFirst("[data-news-body] > div");
        if(body==null)throw new IllegalArgumentException("Official article body structure missing");
        var images=new ArrayList<String>();
        boolean newCardSection=false;
        for(Element block:body.children()) {
            if(block.hasAttr("data-news-rich-text")) {
                // A labelled section may contain several consecutive image grids.
                // The next text block closes it, so reprints/product art stay excluded.
                newCardSection=block.select("p, h2, h3").stream()
                        .map(Element::text).anyMatch(text -> "新規カード".equals(text.strip())||"新カード".equals(text.strip()));
                continue;
            }
            if(!newCardSection)continue;
            if(block.select("[data-news-rich-text]").size()>0){newCardSection=false;continue;}
            for(var button:block.select("[data-news-image-open]")) {
                try {
                    String image=button.absUrl("data-image-src");
                    if(image.isBlank()) {var child=button.selectFirst("img[src]");if(child!=null)image=child.absUrl("src");}
                    images.add(YuGiOhJpUrls.image(image));
                } catch(IllegalArgumentException failure) {
                    log.warn("Skipping malformed official article image: articleUrl={}, reason={}",url,failure.getMessage());
                }
            }
        }
        Instant publishedAt=null;
        var time=article.selectFirst("header time[datetime]");
        if(time!=null)try{publishedAt=Instant.parse(time.attr("datetime"));}
        catch(DateTimeParseException failure){log.warn("Official article date invalid: articleUrl={}",url);}
        if(images.isEmpty())log.warn("No explicit new-card image section found: articleUrl={}",url);
        var product=java.util.regex.Pattern.compile("『([^』]+)』\\s*に収録").matcher(heading.text());
        String productName=product.find()?product.group(1):null;
        if(productName!=null&&productName.length()>1000){log.warn("Ignoring oversized article product name: articleUrl={}",url);productName=null;}
        var textBody=body.clone();
        textBody.select("script,style,iframe,dialog,button,[hidden],[aria-hidden=true]").remove();
        var paragraphs=textBody.select("p,h2,h3,li");
        String text=paragraphs.isEmpty()?textBody.text():paragraphs.stream()
                .filter(element -> element.parents().stream().noneMatch(parent -> paragraphs.contains(parent)))
                .map(Element::wholeText).map(String::strip).filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.joining("\n\n"));
        return new Article(heading.text(),publishedAt,List.copyOf(images),productName,text.isBlank()?null:text);
    }
    public record Article(String title,Instant publishedAt,List<String> imageUrls,String productName,String text){
        public Article(String title,Instant publishedAt,List<String> imageUrls,String productName){this(title,publishedAt,imageUrls,productName,null);}
        public Article(String title,Instant publishedAt,List<String> imageUrls){this(title,publishedAt,imageUrls,null,null);}
    }
}
