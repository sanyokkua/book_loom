package ua.bookloom.pipeline.glossary;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.MeasuredSpeed;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.NamesEstimate;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.glossary.NamesPlan.Minutes;

/**
 * The calls a names pass will send, counted before the first one (15i.N8, 15i review): the names reading of the dial's
 * plan, the batched review of the open names it does not read, one summary per name read at Max (an upper bound), the
 * bare-name knowledge of the names nobody settled (one call per {@value NameKnowledgeCalls#NAME_KNOWLEDGE_BATCH}), the
 * recurring terms (the batch choice over the model scan's pool past the words read one by one, those words, a quarter
 * of the rest as the words the choice keeps — the share the 2026-10-13 rounds kept at most — and the key terms
 * reviewed), and the renderings of names and terms with one further call per batch for refused renderings. The calls
 * are counted by kind and timed by the seconds measured on the chosen model when every kind has them, else at the
 * dial's built-in names-reading pace, scaled by the model's measured seconds per drafted segment.
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
     * The model the per-call times of {@link NamesPlan} were measured on drafted a segment in this many seconds; a
     * model with a measured speed and no measured calls has them scaled by its own seconds over this.
     */
    static final double REFERENCE_SECONDS_PER_SEGMENT = 3.44;

    private static final double SECONDS_PER_MINUTE = 60.0;

    /**
     * Counts the pass's calls of each kind and their time.
     *
     * @param open the names the pass may still change; never null
     * @param story the book's body; never null
     * @param dial the project's quality dial; never null
     * @param language the language the book is read in; null under Auto
     * @param stored the seconds per call of each kind measured on the chosen model; never null, empty for none
     * @return the calls and the minutes they take, as {@link #time} words them
     */
    static NamesEstimate of(
            final List<GlossaryEntry> open,
            final BookSlice story,
            final QualityDial dial,
            @Nullable final String language,
            final Map<CallKind, MeasuredSpeed> stored) {
        Objects.requireNonNull(open, "open");
        Objects.requireNonNull(stored, "stored");
        final NamesPlan plan = NamesPlan.of(dial, NamesPlan.candidates(open, story));
        final Set<String> read = new HashSet<>();
        plan.read().forEach(candidate -> read.add(candidate.entry().id()));
        final int unread =
                (int) open.stream().filter(entry -> !read.contains(entry.id())).count();
        final Map<CallKind, Integer> calls = new EnumMap<>(CallKind.class);
        add(calls, CallKind.TERM_READING, plan.estimatedCalls() + (dial == QualityDial.MAX ? read.size() : 0));
        add(calls, CallKind.REVIEW_TERMS, Math.ceilDiv(unread, TermReview.BATCH_SIZE));
        add(
                calls,
                CallKind.NAME_KNOWLEDGE,
                Math.ceilDiv(NameKnowledgeCalls.askedOf(open).size(), NameKnowledgeCalls.NAME_KNOWLEDGE_BATCH));
        final int terms = termCalls(calls, story, dial, language);
        add(
                calls,
                CallKind.SUGGEST_TARGETS,
                RENDERING_CALLS_PER_BATCH
                        * (Math.ceilDiv(open.size(), SuggestTargets.SUGGEST_BATCH)
                                + Math.ceilDiv(terms, SuggestTargets.SUGGEST_BATCH)));
        log.debug("Names pass estimate dial={} calls={} stored kinds={}", dial, calls, stored.keySet());
        return time(dial, calls, stored);
    }

    /**
     * The time of some calls: the calls times the seconds stored for each kind when every kind has seconds, else the
     * built-in per-call times of the dial, scaled by the model's stored seconds per drafted segment when it has some.
     *
     * @param dial the quality dial whose built-in times apply; never null
     * @param calls the calls of each kind; never null
     * @param stored the seconds per call of each kind measured on the model; never null
     * @return the estimate, {@link NamesEstimate#measured()} when every kind with a call has stored seconds
     */
    static NamesEstimate time(
            final QualityDial dial, final Map<CallKind, Integer> calls, final Map<CallKind, MeasuredSpeed> stored) {
        final Map<CallKind, Integer> counted = new EnumMap<>(CallKind.class);
        calls.forEach((kind, count) -> add(counted, kind, count));
        final int total = counted.values().stream().mapToInt(Integer::intValue).sum();
        if (!counted.isEmpty() && stored.keySet().containsAll(counted.keySet())) {
            final double seconds = counted.entrySet().stream()
                    .mapToDouble(entry -> entry.getValue()
                            * Objects.requireNonNull(stored.get(entry.getKey()), "stored")
                                    .seconds())
                    .sum();
            final int minutes = (int) Math.ceil(seconds / SECONDS_PER_MINUTE);
            log.debug("Names pass estimate measured: {} calls take {} s, {} min", total, seconds, minutes);
            return new NamesEstimate(total, counted, minutes, minutes, true);
        }
        final MeasuredSpeed draft = stored.get(CallKind.DRAFT);
        final double scale = draft == null ? 1.0 : draft.seconds() / REFERENCE_SECONDS_PER_SEGMENT;
        final Minutes minutes = NamesPlan.minutesFor(dial, total, scale);
        log.debug(
                "Names pass estimate from the built-in times: {} calls, scale {} ({}), {}",
                total,
                scale,
                draft == null ? "nothing measured" : "measured per segment",
                minutes);
        return new NamesEstimate(total, counted, minutes.low(), minutes.high(), false);
    }

    private static void add(final Map<CallKind, Integer> calls, final CallKind kind, final int count) {
        if (count > 0) {
            calls.merge(kind, count, Integer::sum);
        }
    }

    // The recurring terms: the batch choice over the pool past the words read one by one, those words and a share of
    // the rest read in windows (none at Fast), and the key terms reviewed (by the batch choice at Fast). Returns all.
    private static int termCalls(
            final Map<CallKind, Integer> calls,
            final BookSlice story,
            final QualityDial dial,
            @Nullable final String language) {
        final int pool = KeyTermScan.frequentWords(story.index(), story.ordinals(), language, LexiconReading.SCAN_POOL)
                .size();
        final int first = Math.min(pool, LexiconReading.readFirst(dial));
        final int rest = pool - first;
        final int keyTerms = KeyTermScan.candidates(story.index(), story.ordinals(), language)
                .size();
        final boolean fast = dial == QualityDial.FAST;
        final int choice =
                Math.ceilDiv(rest, TermChoice.BATCH_SIZE) + (fast ? Math.ceilDiv(keyTerms, TermChoice.BATCH_SIZE) : 0);
        final int reading = fast ? 0 : first + Math.ceilDiv(rest, CHOSEN_SHARE_DIVISOR) + keyTerms;
        add(calls, CallKind.TERM_CHOICE, choice);
        add(calls, CallKind.TERM_READING, reading);
        return choice + reading;
    }
}
