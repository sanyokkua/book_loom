package ua.bookloom.ui;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.ui.state.LiveCalls;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.LiveRow.Stage;

/** Rows of the live card, and calls that carry them, for the tests that publish them to the mirror. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LiveRowFixtures {

    /** The row of paragraph {@code 40 + index} of chapter 7, as a segment {@code s-<index>}. */
    public static LiveRow row(final int index, final Stage stage, final @Nullable String target) {
        return new LiveRow(
                "s-" + index, "ch7 · p" + (40 + index), "Source text " + index, target, stage, null, List.of(), null);
    }

    /** The row of paragraph {@code 40 + index}, drafted again alone because the batch reply {@code code}d it. */
    public static LiveRow fellBack(final int index, final String code, final String target) {
        return new LiveRow(
                "s-" + index,
                "ch7 · p" + (40 + index),
                "Source text " + index,
                target,
                Stage.ACCEPTED,
                code,
                List.of(),
                null);
    }

    /** Rows {@code 1..count} in the given stage, none translated yet. */
    public static List<LiveRow> rows(final int count, final Stage stage) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(n -> row(n, stage, null))
                .toList();
    }

    /** The calls the mirror publishes for {@code rows}, the {@code current} call and no summary. */
    public static LiveCalls calls(final @Nullable CallSnapshot current, final List<LiveRow> rows) {
        return new LiveCalls(current, null, Map.of(), LiveCallFixtures.STARTED.plusSeconds(21), rows, List.of(), 0);
    }

    /** The calls the mirror publishes for {@code rows}, with the last 50 calls the dialog lists. */
    public static LiveCalls calls(final List<LiveRow> rows, final List<CallSnapshot> recent) {
        return new LiveCalls(null, null, Map.of(), LiveCallFixtures.STARTED.plusSeconds(21), rows, recent, 0);
    }
}
