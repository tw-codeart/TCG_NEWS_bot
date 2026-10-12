package com.twcodeart.tcgnews.source.ocg;

import com.rometools.rome.io.*;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.URI;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class YuGiOhJpFeedParser {
    private static final Logger log=LoggerFactory.getLogger(YuGiOhJpFeedParser.class);
    private static final Pattern LINK=Pattern.compile("https://yu-gi-oh\\.jp/news/[A-Za-z0-9_-]+/?");
    private static final Pattern NEW_CARDS=Pattern.compile("(?:新カード|新規カード|新テーマ).{0,100}(?:公開|発表|登場)|収録カード公開",Pattern.DOTALL);
    public List<Post> parse(byte[] bytes) throws IOException,FeedException {
        var input=new SyndFeedInput();input.setAllowDoctypes(false);input.setXmlHealerOn(false);
        var result=new ArrayList<Post>();
        try(var reader=new XmlReader(new ByteArrayInputStream(bytes))) {
            for(var entry:input.build(reader).getEntries())try {
                var bodies=new ArrayList<org.jsoup.nodes.Document>();
                if(entry.getDescription()!=null)bodies.add(fragment(entry.getDescription().getValue()));
                for(var content:entry.getContents())bodies.add(fragment(content.getValue()));
                String title=fragment(entry.getTitle()).text();
                String full=title+"\n"+bodies.stream().map(org.jsoup.nodes.Document::text).collect(java.util.stream.Collectors.joining("\n"));
                String normalized=Normalizer.normalize(full,Normalizer.Form.NFKC);
                if(!normalized.contains("OCG")||!NEW_CARDS.matcher(normalized).find())continue;
                if(title.isBlank()||!NEW_CARDS.matcher(Normalizer.normalize(title,Normalizer.Form.NFKC)).find())
                    title=bodies.stream().map(org.jsoup.nodes.Document::text).filter(text -> NEW_CARDS.matcher(Normalizer.normalize(text,Normalizer.Form.NFKC)).find())
                            .findFirst().orElse(title);
                if(title.isBlank()||title.length()>1000)throw new IllegalArgumentException("Missing or oversized original post title");
                String source=postUrl(entry.getLink()==null||entry.getLink().isBlank()?entry.getUri():entry.getLink());
                var links=new LinkedHashSet<String>();
                for(var body:bodies)for(var anchor:body.select("a[href]"))try{links.add(YuGiOhJpUrls.article(anchor.attr("href")));}
                catch(IllegalArgumentException ignored){ /* Non-article links are expected in a tweet. */ }
                var match=LINK.matcher(full);while(match.find())try{links.add(YuGiOhJpUrls.article(match.group()));}
                catch(IllegalArgumentException failure){log.warn("Ignoring invalid official article link: reason={}",failure.getMessage());}
                if(links.isEmpty()){log.warn("OCG website post has no expanded official article link: sourceUrl={}",source);continue;}
                Instant published=entry.getPublishedDate()==null?null:entry.getPublishedDate().toInstant();
                result.add(new Post(title,source,published,List.copyOf(links)));
            }catch(IllegalArgumentException failure){log.warn("Skipping malformed @yu_gi_oh_jp feed entry: reason={}",failure.getMessage());}
        }
        return List.copyOf(result);
    }
    private org.jsoup.nodes.Document fragment(String value){
        var body=Jsoup.parseBodyFragment(value==null?"":value);body.select("script,style,iframe,blockquote").remove();return body;
    }
    private String postUrl(String value) {
        URI uri=URI.create(value==null?"":value).normalize();String host=uri.getHost();
        if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||!Set.of("x.com","www.x.com","twitter.com","www.twitter.com").contains(host.toLowerCase(Locale.ROOT))
                ||uri.getUserInfo()!=null||uri.getPort()!=-1||uri.toASCIIString().length()>2048
                ||!uri.getPath().matches("(?i)/yu_gi_oh_jp/status/[0-9]+/?"))
            throw new IllegalArgumentException("Post does not belong to @yu_gi_oh_jp");
        return "https://x.com/yu_gi_oh_jp/status/"+uri.getPath().replaceAll("/$","").substring(uri.getPath().lastIndexOf("/status/")+8).replace("/","");
    }
    public record Post(String title,String sourceUrl,Instant publishedAt,List<String> articleUrls){ }
}
