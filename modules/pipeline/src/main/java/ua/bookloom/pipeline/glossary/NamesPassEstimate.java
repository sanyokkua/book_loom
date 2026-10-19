package ua.bookloom.pipeline.glossary;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.NamesEstimate;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.glossary.NamesPlan.Minutes;

/**
 * The calls a names pass will send, counted before the first one (15i.N8, 15i review): the names reading of the dial's
 * plan, the batched review of the open names it does not read, one summary per name read at Max (an upper bound), the
 * recurring terms (the batch choice over the model scan's pool past the words read one by one, those words, a quarter
 * of the rest as the words the choice keeps — the share the 2026-10-13 rounds kept at most — and the key terms
 * reviewed), and the renderings of names and terms with one further call per batch for refused renderings. Every call
 * is timed at the dial's names-reading pace.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NamesPassEstimate {

    /** The share of the batch-chosen pool past the words read one by one that is then read, as a divisor. */
    private static final int CHOSEN_SHARE_DIVISOR = 4;

    /** Each rendering batch may earn one further call for its refused renderings. */
    private static final int RENDERING_CALLS_PER_BATCH = 2;

    /**
     * Counts the pass's calls and their time.
     *
     * @param open the names the pass may still change; never null
     * @param story the book's body; never null
     * @param dial the project's quality dial; never null
     * @param language the language the book is read in; null under Auto
     * @return the calls and the minutes they take
     */
    static NamesEstimate of(
            final List<GlossaryEntry> open,
            final BookSlice story,
            final QualityDial dial,
            @Nullable final String language) {
        Objects.requireNonNull(open, "open");
        final NamesPlan plan = NamesPlan.of(dial, NamesPlan.candidates(open, story));
        final Set<String> read = new HashSet<>();
        plan.read().forEach(candidate -> read.add(candidate.entry().id()));
        final int unread =
                (int) open.stream().filter(entry -> !read.contains(entry.id())).count();
        final int names = plan.estimatedCalls()
                + Math.ceilDiv(unread, TermReview.BATCH_SIZE)
                + (dial == QualityDial.MAX ? read.size() : 0);
        final int terms = termCalls(story, dial, language);
        final int renderings = RENDERING_CALLS_PER_BATCH
                * (Math.ceilDiv(open.size(), SuggestTargets.BATCH_SIZE)
                        + Math.ceilDiv(terms, SuggestTargets.BATCH_SIZE));
        final int calls = names + terms + renderings;
        final Minutes minutes = NamesPlan.minutesFor(dial, calls);
        log.debug(
                "Names pass estimate dial={} names={} terms={} renderings={} calls={}",
                dial,
                names,
                terms,
                renderings,
                calls);
        return new NamesEstimate(calls, minutes.low(), minutes.high());
    }

    private static int termCalls(final BookSlice story, final QualityDial dial, @Nullable final String language) {
        final int pool = KeyTermScan.frequentWords(story.index(), story.ordinals(), language, LexiconReading.SCAN_POOL)
                .size();
        final int first = Math.min(pool, LexiconReading.readFirst(dial));
        final int rest = pool - first;
        final int keyTerms = KeyTermScan.candidates(story.index(), story.ordinals(), language)
                .size();
        final int reviewed = dial == QualityDial.FAST ? Math.ceilDiv(keyTerms, TermChoice.BATCH_SIZE) : keyTerms;
        return Math.ceilDiv(rest, TermChoice.BATCH_SIZE)
                + (dial == QualityDial.FAST ? 0 : first + Math.ceilDiv(rest, CHOSEN_SHARE_DIVISOR))
                + reviewed;
    }
}
