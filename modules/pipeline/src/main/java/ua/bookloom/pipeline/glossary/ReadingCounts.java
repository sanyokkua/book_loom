package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.glossary.ReadingVerdict.ReadCall;
import ua.bookloom.pipeline.glossary.TermReading.Kind;
import ua.bookloom.pipeline.glossary.TermReading.Usage;
import ua.bookloom.pipeline.glossary.TermReading.WindowReading;
import ua.bookloom.pipeline.index.Window;

/**
 * What a term's reading calls add up to (15i.N5): each window counted once, as the reading kept its answer — the
 * majority of the usages, the kind most of the name windows give, the gender votes, and the ordinary-word uses whose
 * quote stands in the window it answers.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReadingCounts {

    /**
     * How a group of windows reads a term: a usage, and for a name what it names.
     *
     * @param usage how the windows use the term; never {@link Usage#UNSURE}
     * @param kind what the term names; {@link Kind#OTHER} for any usage but a name, so two readings of an ordinary
     *     word are one reading whatever kind each window gave
     */
    record Read(Usage usage, Kind kind) {

        /**
         * A reading, its kind kept only for a name.
         *
         * @param usage how the windows use the term
         * @param kind what the windows say it names
         * @return the reading; never null
         */
        static Read of(final Usage usage, final Kind kind) {
            return new Read(usage, usage == Usage.NAME ? kind : Kind.OTHER);
        }
    }

    /** Every window reading of some calls, in call order. */
    static List<WindowReading> readings(final List<ReadCall> calls) {
        return calls.stream().flatMap(call -> call.reading().windows().stream()).toList();
    }

    /** How many windows some calls showed, answered or not. */
    static int shown(final List<ReadCall> calls) {
        return calls.stream().mapToInt(call -> call.windows().size()).sum();
    }

    /** How many window readings use the term one way. */
    static long uses(final List<WindowReading> readings, final Usage usage) {
        return readings.stream().filter(reading -> reading.usage() == usage).count();
    }

    /**
     * The reading most windows give: the usage with the most windows (an unsure window is no vote), and for a name the
     * kind most of its name windows give.
     *
     * @param readings the window readings
     * @return the reading, or null when no window has a usage or two usages tie
     */
    static @Nullable Read majority(final List<WindowReading> readings) {
        final Usage usage = top(
                readings.stream()
                        .filter(reading -> reading.usage() != Usage.UNSURE)
                        .toList(),
                WindowReading::usage,
                Usage.class);
        if (usage == null) {
            return null;
        }
        final Kind kind = top(
                readings.stream().filter(reading -> reading.usage() == usage).toList(),
                WindowReading::kind,
                Kind.class);
        return Read.of(usage, kind == null ? Kind.OTHER : kind);
    }

    /** Each call's own majority reading, a call with none left out, in call order. */
    static List<Read> callReads(final List<ReadCall> calls) {
        final List<Read> reads = new ArrayList<>();
        calls.forEach(call -> {
            final Read read = majority(call.reading().windows());
            if (read != null) {
                reads.add(read);
            }
        });
        return reads;
    }

    /** How many windows of some calls vote for a gender: a vote the reading kept because its quote bears it out. */
    static int votes(final List<ReadCall> calls, final Gender gender) {
        // The calls of a cluster's spellings may show one window twice: a quote in one paragraph votes once.
        final Set<String> seen = new HashSet<>();
        for (final ReadCall call : calls) {
            for (final WindowReading reading : call.reading().windows()) {
                if (reading.vote() == gender) {
                    seen.add(call.windows().get(reading.number() - 1).firstOrdinal() + "\u0000"
                            + reading.quote().strip().toLowerCase(Locale.ROOT));
                }
            }
        }
        return seen.size();
    }

    /**
     * The quotes of the ordinary-word uses that stand in the window they answer, so a phrase copied from another window
     * is no second use.
     *
     * @param calls the term's own calls
     * @return the standing quotes, in call and window order; never null
     */
    static List<String> standingWordQuotes(final List<ReadCall> calls) {
        final List<String> quotes = new ArrayList<>();
        for (final ReadCall call : calls) {
            for (final WindowReading reading : call.reading().windows()) {
                if (reading.usage() == Usage.WORD && standsIn(reading, call.windows())) {
                    quotes.add(reading.quote());
                }
            }
        }
        log.debug("Reading counts: {} ordinary-word uses stand in their windows", quotes.size());
        return quotes;
    }

    /** The windows of a call whose reading votes for a gender, in window order. */
    static List<Window> votingWindows(final ReadCall call, final Gender gender) {
        return call.reading().windows().stream()
                .filter(reading -> reading.vote() == gender)
                .map(reading -> call.windows().get(reading.number() - 1))
                .toList();
    }

    /** The windows of a call that read the term one way, in window order. */
    static List<Window> windowsReading(final ReadCall call, final Read read) {
        return call.reading().windows().stream()
                .filter(reading -> Read.of(reading.usage(), reading.kind()).equals(read))
                .map(reading -> call.windows().get(reading.number() - 1))
                .toList();
    }

    // A call's readings name only windows it showed (ReadCall), so the number always finds its window.
    private static boolean standsIn(final WindowReading reading, final List<Window> windows) {
        return QuoteVote.stands(
                reading.quote(), windows.get(reading.number() - 1).text());
    }

    // The value most readings give, or null when none does or two tie for the most.
    private static <E extends Enum<E>> @Nullable E top(
            final List<WindowReading> readings, final Function<WindowReading, E> value, final Class<E> type) {
        final Map<E, Integer> counts = new EnumMap<>(type);
        readings.forEach(reading -> counts.merge(value.apply(reading), 1, Integer::sum));
        final List<Map.Entry<E, Integer>> ranked = counts.entrySet().stream()
                .sorted(Map.Entry.<E, Integer>comparingByValue(Comparator.reverseOrder()))
                .toList();
        if (ranked.isEmpty()
                || (ranked.size() > 1
                        && Objects.equals(
                                ranked.get(0).getValue(), ranked.get(1).getValue()))) {
            return null;
        }
        return ranked.getFirst().getKey();
    }
}
