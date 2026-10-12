package com.twcodeart.tcgnews.source.ocg;

/** Standalone packaging diagnostic: no Spring context, database, scraping pipeline or QQ transport. */
public final class ArticleReaderCheck {
    public static void main(String[] args) throws Exception {
        String url="https://yu-gi-oh.jp/news/w0cgkf8x-usk/";
        String browser=System.getenv("YUGIOH_JP_BROWSER_EXECUTABLE");
        String html=browser==null||browser.isBlank()?new YuGiOhJpArticleClient().fetch(url):new ArticleBrowserReader().read(browser,url);
        var article=new YuGiOhJpArticleParser().parse(html,url);
        if(article.imageUrls().isEmpty())throw new java.io.IOException("No explicit new-card images found");
        System.out.println("ARTICLE_READ_OK: newCards="+article.imageUrls().size()+", saved=0, notifications=0");
    }
    private ArticleReaderCheck(){ }
}
