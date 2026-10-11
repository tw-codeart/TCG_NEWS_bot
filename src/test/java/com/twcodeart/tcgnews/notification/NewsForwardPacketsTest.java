package com.twcodeart.tcgnews.notification;
import com.twcodeart.tcgnews.news.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
class NewsForwardPacketsTest {
    @Test void keepsFourImagesTogetherWhenEighteenEarlierImagesLeaveOnlyTwoSlots() {
        var items=new java.util.ArrayList<NewsItem>();items.addAll(post(1,18));items.addAll(post(2,4));
        assertThat(NewsForwardPackets.plan(items,20)).extracting(List::size).containsExactly(18,4);
    }
    @Test void splitsAnOversizedPostWithoutDroppingAnyImage() {
        assertThat(NewsForwardPackets.plan(post(1,45),20)).extracting(List::size).containsExactly(20,20,5);
    }
    private List<NewsItem> post(int id,int images) {
        return IntStream.range(0,images).mapToObj(n -> new NewsItem(Game.WEISS_SCHWARZ,NewsCategory.CARD_REVEAL,
                "今日のカード",null,null,"https://pbs.twimg.com/media/"+id+"-"+n+".png",
                "https://x.com/wstcg/status/"+id,String.format("%064x",id*100+n))).toList();
    }

    @Test void mergesSeveralPostsInOnePacketAndSplitsOnlyAtTheBoundedCapacity() {
        var items=new java.util.ArrayList<NewsItem>();
        items.addAll(post(1,13));items.addAll(post(2,13));items.addAll(post(3,19));
        assertThat(NewsForwardPackets.plan(items,50)).extracting(List::size).containsExactly(45);
        items.addAll(post(4,10));
        assertThat(NewsForwardPackets.plan(items,50)).extracting(List::size).containsExactly(45,10);
        assertThat(NewsForwardPackets.plan(items,50).stream().flatMap(List::stream).toList()).hasSize(55);
    }
}
