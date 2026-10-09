package com.twcodeart.tcgnews.notification;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Keeps a normal post's images together while bounding each transport packet. */
public final class NewsForwardPackets {
    private NewsForwardPackets() { }
    public static List<List<NewsItem>> plan(List<NewsItem> items, int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("maximum must be positive");
        var posts = new LinkedHashMap<PostKey, List<NewsItem>>();
        for (var item : items) posts.computeIfAbsent(new PostKey(item.getGame(), item.getSourceUrl()),
                ignored -> new ArrayList<>()).add(item);
        var packets = new ArrayList<List<NewsItem>>();
        var current = new ArrayList<NewsItem>();
        for (var post : posts.values()) {
            for (int offset=0; offset<post.size(); offset+=maximum) {
                var chunk=post.subList(offset, Math.min(post.size(),offset+maximum));
                if (!current.isEmpty() && current.size()+chunk.size()>maximum) {
                    packets.add(List.copyOf(current)); current.clear();
                }
                current.addAll(chunk);
            }
        }
        if (!current.isEmpty()) packets.add(List.copyOf(current));
        return List.copyOf(packets);
    }
    private record PostKey(Game game, String sourceUrl) { }
}
