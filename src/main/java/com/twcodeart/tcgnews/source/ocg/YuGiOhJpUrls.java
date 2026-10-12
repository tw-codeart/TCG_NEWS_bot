package com.twcodeart.tcgnews.source.ocg;

import java.net.URI;

/** Only the official news article namespace is eligible for link following. */
public final class YuGiOhJpUrls {
    public static String article(String value) {
        URI uri=URI.create(value).normalize();
        if(!"https".equalsIgnoreCase(uri.getScheme()) || !"yu-gi-oh.jp".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo()!=null || uri.getPort()!=-1 || uri.getPath()==null
                || !uri.getPath().matches("/news/[A-Za-z0-9_-]+/?") || uri.toASCIIString().length()>2048)
            throw new IllegalArgumentException("Unsupported official YU-GI-OH.jp article URL");
        return "https://yu-gi-oh.jp"+uri.getPath().replaceAll("/*$", "")+"/";
    }
    public static String image(String value) {
        URI uri=URI.create(value).normalize();
        if(!"https".equalsIgnoreCase(uri.getScheme()) || !"yu-gi-oh.jp".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo()!=null || uri.getPort()!=-1 || uri.getFragment()!=null
                || !uri.getPath().startsWith("/media/cms/v1/") || uri.toASCIIString().length()>2048)
            throw new IllegalArgumentException("Unsupported official YU-GI-OH.jp card image URL");
        return uri.toASCIIString();
    }
    private YuGiOhJpUrls(){ }
}
