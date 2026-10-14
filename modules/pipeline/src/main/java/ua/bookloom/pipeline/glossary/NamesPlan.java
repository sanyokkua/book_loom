package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.index.Case;
import ua.bookloom.pipeline.index.Mention;
import ua.bookloom.pipeline.index.Window;
import ua.bookloom.pipeline.index.WindowPlan;

/**
 * Which glossary terms the model reads and how (15i.N4): per quality dial the terms read, the windows each gets and how
 * many windows go into one call, so the names step stays inside its time budget (Fast 5–10, Balanced 15–25, Max 30–60
 * minutes on a novel of about 80k words on a 26b model). The budget is held by the dial's thresholds and by reading at
 * most {@link #MAX_READ_TERMS} terms, the most mentioned first.
 *
 * @param dial the quality dial the plan is for
 * @param read the candidates read, most mentions first, at most {@link #MAX_READ_TERMS}
 * @param windows how many windows a term gets, how many neighbouring segments each reads and its token budget
 * @param windowsPerCall the most windows one call shows; positive
 */
@Slf4j
public record NamesPlan(QualityDial dial, List<Candidate> read, WindowPlan windows, int windowsPerCall) {

    /** The most terms one pass reads, whatever the dial: the cap that holds the time budget. */
    public static final int MAX_READ_TERMS = 80;

    /** Fast reads only a likely character the story names this often. */
    static final int FAST_MIN_MENTIONS = 5;

    /** Balanced and Max read any candidate the story names this often. */
    static final int MIN_MENTIONS = 3;

    private static final WindowPlan FAST_WINDOWS = new WindowPlan(4, 1, 350);
    private static final WindowPlan BALANCED_WINDOWS = new WindowPlan(6, 2, 450);
    private static final WindowPlan MAX_WINDOWS = new WindowPlan(12, 2, 450);
    private static final int FAST_PER_CALL = 4;
    private static final int PER_CALL = 6;
    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final long FAST_LOW_MILLIS = 3_750L;
    private static final long FAST_HIGH_MILLIS = 7_500L;
    private static final long SLOW_LOW_MILLIS = 11_250L;
    private static final long BALANCED_HIGH_MILLIS = 18_750L;
    private static final long MAX_HIGH_MILLIS = 22_500L;

    /** Copies the list and rejects a call that could show no window. */
    public NamesPlan {
        Objects.requireNonNull(dial, "dial");
        Objects.requireNonNull(windows, "windows");
        if (windowsPerCall < 1) {
            throw new IllegalArgumentException("windowsPerCall must be positive: " + windowsPerCall);
        }
        read = List.copyOf(read);
    }

    /**
     * A glossary entry and how often the story writes it.
     *
     * @param entry the entry
     * @param mentions its case-sensitive mentions in the story
     */
    public record Candidate(GlossaryEntry entry, int mentions) {}

    /**
     * The plan of a dial over some candidates.
     *
     * @param dial the quality dial; never null
     * @param candidates the candidates, in glossary order; never null
     * @return the plan; never null
     */
    public static NamesPlan of(final QualityDial dial, final List<Candidate> candidates) {
        Objects.requireNonNull(dial, "dial");
        Objects.requireNonNull(candidates, "candidates");
        final int minimum = dial == QualityDial.FAST ? FAST_MIN_MENTIONS : MIN_MENTIONS;
        final List<Candidate> read = candidates.stream()
                .filter(candidate -> candidate.mentions() >= minimum)
                .filter(candidate -> dial != QualityDial.FAST || isLikelyCharacter(candidate.entry()))
                .sorted(Comparator.comparingInt(Candidate::mentions).reversed())
                .limit(MAX_READ_TERMS)
                .toList();
        final NamesPlan plan =
                switch (dial) {
                    case FAST -> new NamesPlan(dial, read, FAST_WINDOWS, FAST_PER_CALL);
                    case BALANCED -> new NamesPlan(dial, read, BALANCED_WINDOWS, PER_CALL);
                    case MAX -> new NamesPlan(dial, read, MAX_WINDOWS, PER_CALL);
                };
        log.debug(
                "Names plan {}: {} of {} candidates read (at least {} mentions), {} calls estimated",
                dial,
                read.size(),
                candidates.size(),
                minimum,
                plan.estimatedCalls());
        return plan;
    }

    // A character, or an untyped name a first-name list or the pronouns already took for a person.
    private static boolean isLikelyCharacter(final GlossaryEntry entry) {
        return entry.type() == TermType.CHARACTER || entry.gender() != Gender.UNKNOWN;
    }

    /**
     * Counts each entry's case-sensitive mentions in a slice of the book, by the book index's one matching rule.
     *
     * @param entries the entries; never null
     * @param story the slice whose segments count; never null
     * @return one candidate per entry, in the order given; never null
     */
    public static List<Candidate> candidates(final List<GlossaryEntry> entries, final BookSlice story) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(story, "story");
        final Set<Integer> slice = new HashSet<>(story.ordinals());
        final List<Candidate> found = entries.stream()
                .map(entry ->
                        new Candidate(entry, mentionsOf(entry, story, slice).size()))
                .toList();
        log.debug("Names plan candidates: {} entries counted over {} segments", entries.size(), slice.size());
        return found;
    }

    /**
     * Cuts the windows of one entry the plan reads: its case-sensitive mentions in the slice, narration preferred.
     *
     * @param entry the entry; never null
     * @param story the slice whose segments count; never null
     * @return the windows in reading order, at most {@code windows().count()}; never null, empty when the story never
     *     writes the entry
     */
    public List<Window> windowsOf(final GlossaryEntry entry, final BookSlice story) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(story, "story");
        final List<Mention> mentions = mentionsOf(entry, story, new HashSet<>(story.ordinals()));
        log.debug("Names plan windows of entry {}: {} mentions, plan {}", entry.id(), mentions.size(), windows);
        log.trace("Names plan windows of {}", entry.term());
        return story.index().windows(mentions, windows);
    }

    private static List<Mention> mentionsOf(
            final GlossaryEntry entry, final BookSlice story, final Set<Integer> slice) {
        return story.index().mentions(entry.names(), Case.SENSITIVE).stream()
                .filter(mention -> slice.contains(mention.ordinal()))
                .toList();
    }

    /**
     * Estimates the calls the plan makes before the first one.
     *
     * @return the calls, one per group of at most {@link #windowsPerCall()} windows of each term read; a term gets at
     *     most as many windows as it has mentions
     */
    public int estimatedCalls() {
        return read.stream()
                .mapToInt(candidate -> Math.ceilDiv(Math.min(windows.count(), candidate.mentions()), windowsPerCall))
                .sum();
    }

    /**
     * How long the plan's calls take, as a range in whole minutes.
     *
     * @param low the fewest minutes, rounded up
     * @param high the most minutes, rounded up; at least {@code low}
     */
    public record Minutes(int low, int high) {}

    /**
     * Estimates how long the plan's calls take. The dial's time budget (Fast 5–10, Balanced 15–25, Max 30–60 minutes)
     * is what the plan with the most calls it can make takes, spread over those calls, so a smaller book is told a
     * shorter time instead of the whole budget.
     *
     * @return the fewest and the most whole minutes, each rounded up; zero for a plan that reads nothing
     */
    public Minutes estimatedMinutes() {
        return minutesFor(dial, estimatedCalls());
    }

    /**
     * How long a number of calls takes at a dial, at the per-call time of its names reading.
     *
     * @param dial the quality dial; never null
     * @param calls the calls; not negative
     * @return the fewest and the most whole minutes, each rounded up; zero for no call
     */
    public static Minutes minutesFor(final QualityDial dial, final int calls) {
        Objects.requireNonNull(dial, "dial");
        final Minutes minutes = new Minutes((int) Math.ceilDiv(calls * lowMillisPerCall(dial), MILLIS_PER_MINUTE), (int)
                Math.ceilDiv(calls * highMillisPerCall(dial), MILLIS_PER_MINUTE));
        log.debug("Names plan {}: {} calls take {}", dial, calls, minutes);
        return minutes;
    }

    // A call's seconds at the dial: 3.75 to 7.5 at Fast, 11.25 to 18.75 at Balanced, 11.25 to 22.5 at Max.
    private static long lowMillisPerCall(final QualityDial dial) {
        return dial == QualityDial.FAST ? FAST_LOW_MILLIS : SLOW_LOW_MILLIS;
    }

    private static long highMillisPerCall(final QualityDial dial) {
        return switch (dial) {
            case FAST -> FAST_HIGH_MILLIS;
            case BALANCED -> BALANCED_HIGH_MILLIS;
            case MAX -> MAX_HIGH_MILLIS;
        };
    }

    /**
     * Cuts one term's windows into the groups its calls show, as even as they can be, so a seventh window is not read
     * alone.
     *
     * @param termWindows the term's windows in reading order; never null
     * @return the groups in reading order; never null, empty when there are no windows
     */
    public List<List<Window>> calls(final List<Window> termWindows) {
        Objects.requireNonNull(termWindows, "termWindows");
        final int total = termWindows.size();
        final int groups = Math.ceilDiv(total, windowsPerCall);
        final List<List<Window>> calls = new ArrayList<>();
        int from = 0;
        for (int group = 0; group < groups; group++) {
            final int size = total / groups + (group < total % groups ? 1 : 0);
            calls.add(List.copyOf(termWindows.subList(from, from + size)));
            from += size;
        }
        log.debug("Names plan calls: {} windows in {} calls of at most {}", total, groups, windowsPerCall);
        return List.copyOf(calls);
    }
}
