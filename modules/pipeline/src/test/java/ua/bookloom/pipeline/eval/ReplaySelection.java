package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.eval.LogReplayCorpus.Outcome;

/**
 * The fast replay's selection: the draft calls of a log, stratified by length (short or long source), shape (single or
 * batch) and how their segments ended (flagged, repaired, accepted), about {@code target} of them. Every call that
 * drafted a segment that ended flagged is kept first, so the selection may be larger than the target; the rest is dealt
 * out across the strata in turn, each stratum spread over the log rather than taken from its start.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReplaySelection {

    /** A source longer than this many characters is a long call: the length at which a small model starts to drop clauses. */
    static final int LONG_OVER = 250;

    /** One stratum of the selection. */
    record Cell(boolean isLong, boolean isBatch, Outcome outcome) {}

    static Cell cellOf(final LogReplayCorpus corpus, final LoggedCall call) {
        return new Cell(call.sourceLength() > LONG_OVER, call.isBatch(), corpus.outcomeOf(call));
    }

    static List<LoggedCall> fast(final LogReplayCorpus corpus, final int target) {
        final List<LoggedCall> drafts = corpus.calls(CallKind.DRAFT).stream()
                .filter(call -> !call.sourceTexts().isEmpty())
                .toList();
        final List<LoggedCall> picked = new ArrayList<>();
        final Map<Cell, List<LoggedCall>> queues = new LinkedHashMap<>();
        for (final LoggedCall call : drafts) {
            final Cell cell = cellOf(corpus, call);
            if (cell.outcome() == Outcome.FLAGGED) {
                picked.add(call);
            } else {
                queues.computeIfAbsent(cell, key -> new ArrayList<>()).add(call);
            }
        }
        queues.replaceAll((cell, calls) -> spread(calls));
        for (int round = 0; picked.size() < target && hasRound(queues, round); round++) {
            for (final List<LoggedCall> queue : queues.values()) {
                if (queue.size() > round && picked.size() < target) {
                    picked.add(queue.get(round));
                }
            }
        }
        picked.sort(Comparator.comparingInt(LoggedCall::order));
        return List.copyOf(picked);
    }

    private static boolean hasRound(final Map<Cell, List<LoggedCall>> queues, final int round) {
        return queues.values().stream().anyMatch(queue -> queue.size() > round);
    }

    // Bit-reversed index order visits a list's start, middle, quarters … so any prefix is spread over the whole log.
    private static List<LoggedCall> spread(final List<LoggedCall> calls) {
        return IntStream.range(0, calls.size())
                .boxed()
                .sorted((a, b) -> Integer.compareUnsigned(Integer.reverse(a), Integer.reverse(b)))
                .map(calls::get)
                .toList();
    }
}
