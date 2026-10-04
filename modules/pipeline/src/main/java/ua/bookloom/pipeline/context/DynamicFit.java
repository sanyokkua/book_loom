package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.SnapshotTmHit;
import ua.bookloom.api.project.SnapshotTmHit.TmHitKind;
import ua.bookloom.pipeline.chunk.TokenEstimator;

/**
 * The dynamic context cut to the room the budget gave it, section by section in priority order. The cut happens before
 * the snapshot is built, so what a retry replays is exactly what the first draft was shown.
 *
 * @param terms the glossary terms that fit, in their original order
 * @param hits the memory hits that fit; reuse hits, which the prompt does not show, always stay
 * @param preceding the newest preceding texts that fit, in document order
 * @param summary the summary when it fit, else null
 * @param used the tokens each section took
 */
@Slf4j
record DynamicFit(
        List<InjectedTerm> terms,
        List<SnapshotTmHit> hits,
        List<String> preceding,
        @Nullable String summary,
        Map<ContextSection, Integer> used) {

    static DynamicFit of(
            final List<InjectedTerm> terms,
            final List<SnapshotTmHit> hits,
            final List<String> preceding,
            @Nullable final String summary,
            final int allowance) {
        final Kept<InjectedTerm> keptTerms = take(terms, DynamicFit::termCost, allowance, false);
        final Kept<SnapshotTmHit> keptHits = take(hits, DynamicFit::hitCost, allowance - keptTerms.tokens(), false);
        final int afterHits = allowance - keptTerms.tokens() - keptHits.tokens();
        final Kept<String> keptPreceding = take(preceding, DynamicFit::estimate, afterHits, true);
        final int afterPreceding = afterHits - keptPreceding.tokens();
        final int summaryTokens = summary == null ? 0 : estimate(summary);
        final boolean summaryFits = summary != null && summaryTokens <= afterPreceding;
        final DynamicFit fit = new DynamicFit(
                keptTerms.items(),
                keptHits.items(),
                keptPreceding.items(),
                summaryFits ? summary : null,
                used(keptTerms.tokens(), keptHits.tokens(), keptPreceding.tokens(), summaryFits ? summaryTokens : 0));
        log(fit, terms.size(), hits.size(), preceding.size(), summary != null, allowance);
        return fit;
    }

    private static Map<ContextSection, Integer> used(
            final int glossary, final int memory, final int preceding, final int summary) {
        final Map<ContextSection, Integer> used = new EnumMap<>(ContextSection.class);
        used.put(ContextSection.GLOSSARY, glossary);
        used.put(ContextSection.MEMORY, memory);
        used.put(ContextSection.PRECEDING, preceding);
        used.put(ContextSection.SUMMARY, summary);
        return used;
    }

    private static <T> Kept<T> take(
            final List<T> items, final ToIntFunction<T> cost, final int room, final boolean newestFirst) {
        final List<T> ordered = new ArrayList<>(items);
        if (newestFirst) {
            Collections.reverse(ordered);
        }
        final List<T> kept = new ArrayList<>();
        int tokens = 0;
        for (final T item : ordered) {
            final int next = tokens + cost.applyAsInt(item);
            if (next > room) {
                break;
            }
            kept.add(item);
            tokens = next;
        }
        if (newestFirst) {
            Collections.reverse(kept);
        }
        return new Kept<>(kept, tokens);
    }

    private static int termCost(final InjectedTerm term) {
        return estimate(String.join("\n", term.lines()));
    }

    private static int hitCost(final SnapshotTmHit hit) {
        return hit.kind() == TmHitKind.CONTEXT ? 0 : estimate(hit.source() + " → " + hit.target());
    }

    private static int estimate(final String text) {
        return TokenEstimator.estimate(text, null);
    }

    private static void log(
            final DynamicFit fit,
            final int terms,
            final int hits,
            final int preceding,
            final boolean hadSummary,
            final int allowance) {
        log.debug(
                "Dynamic context fitted allowance={} glossary={}/{}t{} memory={}/{}t{} preceding={}/{}t{} summary={}/{}t{}",
                allowance,
                fit.terms().size(),
                terms,
                fit.used().get(ContextSection.GLOSSARY),
                fit.hits().size(),
                hits,
                fit.used().get(ContextSection.MEMORY),
                fit.preceding().size(),
                preceding,
                fit.used().get(ContextSection.PRECEDING),
                fit.summary() != null,
                hadSummary,
                fit.used().get(ContextSection.SUMMARY));
    }

    private record Kept<T>(List<T> items, int tokens) {}
}
