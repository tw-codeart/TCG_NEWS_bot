package com.twcodeart.tcgnews.source.ocg;

import java.text.Normalizer;
import java.util.regex.Pattern;
import java.util.function.Predicate;

/** Conservative announcement headings, not a claim of database-verified card novelty. */
public final class OcgNewCardPostFilter implements Predicate<String> {
    private static final Pattern EXCLUDED = Pattern.compile("再録|再収録|復刻|新規イラスト|イラスト違い|キャンペーン|プレゼント");
    private static final Pattern REVEAL = Pattern.compile("収録カード公開|新(?:カード|規カード).{0,30}(?:公開|発表|収録|登場)", Pattern.DOTALL);

    @Override
    public boolean test(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return !EXCLUDED.matcher(normalized).find() && REVEAL.matcher(normalized).find();
    }
}
