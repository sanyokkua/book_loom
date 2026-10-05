package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.SnapshotRendering;
import ua.bookloom.api.project.SnapshotTmHit;
import ua.bookloom.api.project.SnapshotTmHit.TmHitKind;
import ua.bookloom.pipeline.chunk.TokenEstimator;

/**
 * The dynamic context cut to the room the budget gave it, section by section in priority order. The cut happens before
 * the snapshot is built, so what a retry replays is exactly what the first draft was shown.
 *
 * @param terms the glossary terms that fit, in their original order
 * @param characters the character gender lines that fit their own share
 * @param lexicon the recurring-term renderings that fit their own share
 * @param hits the memory hits that fit; reuse hits, which the prompt does not show, always stay
 * @param preceding the newest preceding texts that fit, in document order
 * @param summary the summary when it fit, else null
 * @param used the tokens each section took
 */
@Slf4j
record DynamicFit(
        List<InjectedTerm> terms,
        List<String> characters,
        List<SnapshotRendering> lexicon,
        List<SnapshotTmHit> hits,
        List<String> preceding,
        @Nullable String summary,
        Map<ContextSection, Integer> used) {

    static DynamicFit of(
            final List<InjectedTerm> terms,
            final List<String> characters,
            final List<SnapshotRendering> lexicon,
            final List<SnapshotTmHit> hits,
            final List<String> preceding,
            @Nullable final String summary,
            final int allowance) {
        final Kept<InjectedTerm> keptTerms = take(terms, DynamicFit::termCost, allowance, false);
        final int afterTerms = allowance - keptTerms.tokens();
        final Kept<String> keptCharacters = characterShare(characters, afterTerms);
        final Kept<SnapshotRendering> keptLexicon = lexiconShare(lexicon, afterTerms);
        final int afterShares = afterTerms - keptCharacters.tokens() - keptLexicon.tokens();
        final Kept<SnapshotTmHit> keptHits = take(hits, DynamicFit::hitCost, afterShares, false);
        final int afterHits = afterShares - keptHits.tokens();
        final Kept<String> keptPreceding = take(preceding, DynamicFit::estimate, afterHits, true);
        final int afterPreceding = afterHits - keptPreceding.tokens();
        final int summaryTokens = summary == null ? 0 : estimate(summary);
        final boolean summaryFits = summary != null && summaryTokens <= afterPreceding;
        final DynamicFit fit = new DynamicFit(
                keptTerms.items(),
                keptCharacters.items(),
                keptLexicon.items(),
                keptHits.items(),
                keptPreceding.items(),
                summaryFits ? summary : null,
                used(
                        keptTerms.tokens(),
                        keptCharacters.tokens(),
                        keptLexicon.tokens(),
                        keptHits.tokens(),
                        keptPreceding.tokens(),
                        summaryFits ? summaryTokens : 0));
        log(fit, Offered.of(terms, characters, lexicon, hits, preceding, summary), allowance);
        return fit;
    }

    private static Kept<String> characterShare(final List<String> characters, final int room) {
        return take(characters, DynamicFit::estimate, ContextBudget.characterAllowance(room), false);
    }

    private static Kept<SnapshotRendering> lexiconShare(final List<SnapshotRendering> lexicon, final int room) {
        return take(lexicon, DynamicFit::renderingCost, ContextBudget.lexiconAllowance(room), false);
    }

    private static Map<ContextSection, Integer> used(
            final int glossary,
            final int characters,
            final int lexicon,
            final int memory,
            final int preceding,
            final int summary) {
        final Map<ContextSection, Integer> used = new EnumMap<>(ContextSection.class);
        used.put(ContextSection.GLOSSARY, glossary);
        used.put(ContextSection.CHARACTERS, characters);
        used.put(ContextSection.LEXICON, lexicon);
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

    private static int renderingCost(final SnapshotRendering rendering) {
        return estimate(rendering.term() + " → " + rendering.rendering());
    }

    private static int hitCost(final SnapshotTmHit hit) {
        return hit.kind() == TmHitKind.CONTEXT ? 0 : estimate(hit.source() + " → " + hit.target());
    }

    private static int estimate(final String text) {
        return TokenEstimator.estimate(text, null);
    }

    private static void log(final DynamicFit fit, final Offered offered, final int allowance) {
        log.debug(
                "Dynamic context fitted allowance={} glossary={}/{}t{} characters={}/{}t{} lexicon={}/{}t{} memory={}/{}t{} preceding={}/{}t{} summary={}/{}t{}",
                allowance,
                fit.terms().size(),
                offered.terms(),
                fit.used().get(ContextSection.GLOSSARY),
                fit.characters().size(),
                offered.characters(),
                fit.used().get(ContextSection.CHARACTERS),
                fit.lexicon().size(),
                offered.lexicon(),
                fit.used().get(ContextSection.LEXICON),
                fit.hits().size(),
                offered.hits(),
                fit.used().get(ContextSection.MEMORY),
                fit.preceding().size(),
                offered.preceding(),
                fit.used().get(ContextSection.PRECEDING),
                fit.summary() != null,
                offered.hadSummary(),
                fit.used().get(ContextSection.SUMMARY));
    }

    private record Offered(int terms, int characters, int lexicon, int hits, int preceding, boolean hadSummary) {

        static Offered of(
                final List<InjectedTerm> terms,
                final List<String> characters,
                final List<SnapshotRendering> lexicon,
                final List<SnapshotTmHit> hits,
                final List<String> preceding,
                @Nullable final String summary) {
            return new Offered(
                    terms.size(), characters.size(), lexicon.size(), hits.size(), preceding.size(), summary != null);
        }
    }

    private record Kept<T>(List<T> items, int tokens) {}
}
