package ua.bookloom.pipeline.revision;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.lexicon.TermMatch;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * The model's check of decided paragraphs against their neighbours. Each paragraph that was repaired, flagged or carries
 * a finding is shown to the model with the translated paragraph before it and after it, the names and terms the book
 * holds and the source, and the model fixes what disagrees. Only a machine-owned target is ever replaced: the person's
 * own edit is not touched. An answer that changes nothing stores nothing, and one that fails a gate or a check is
 * dropped by the revision call.
 */
@Slf4j
@RequiredArgsConstructor
final class NeighbourRevision {

    private static final int MAX_FACT_LINES = 12;

    private final SegmentRepository segments;
    private final RevisionWriter writer;
    private final RevisionCall call;

    /**
     * Checks every risky paragraph of the book against its neighbours.
     *
     * @param inputs what the pass read as it started
     * @param calls the seam every call is sent through
     * @param tally where each correction is counted
     * @return ok once every such paragraph is checked, or the error that ended the pass
     */
    Result<Boolean> revise(final PassInputs inputs, final ModelCalls calls, final PassTally tally) {
        final Result<List<SegmentRecord>> read = segments.all(inputs.projectId());
        if (read.isErr()) {
            return Result.err(Objects.requireNonNull(read.error(), "error"));
        }
        final Map<String, SegmentRecord> byId = new HashMap<>();
        Objects.requireNonNull(read.data(), "records").forEach(record -> byId.put(record.segmentId(), record));
        final List<String> order = inputs.order();
        log.info("Neighbour check started project={} segments={}", inputs.projectId(), order.size());
        int checked = 0;
        for (int at = 0; at < order.size(); at++) {
            final SegmentRecord record = byId.get(order.get(at));
            if (!isRisky(record)) {
                continue;
            }
            checked++;
            final Result<Boolean> done = checkOne(inputs, order, at, byId, calls, tally);
            if (done.isErr()) {
                return done;
            }
        }
        log.info("Neighbour check ended project={} checked={}", inputs.projectId(), checked);
        return Result.ok(true);
    }

    // Repaired or flagged: the shapes where a defect is likely. A clean accepted draft, even one carrying a soft note,
    // is
    // left alone, so the pass costs a call only for the paragraphs the run already had trouble with.
    private static boolean isRisky(@Nullable final SegmentRecord record) {
        if (record == null || record.userTarget() != null || record.maskedMachineTarget() == null) {
            return false;
        }
        final boolean decided = record.status() != SegmentStatus.PENDING;
        return decided
                && record.path() != SegmentPath.VERBATIM
                && (record.status() == SegmentStatus.FLAGGED || record.path() == SegmentPath.REPAIRED);
    }

    private Result<Boolean> checkOne(
            final PassInputs inputs,
            final List<String> order,
            final int at,
            final Map<String, SegmentRecord> byId,
            final ModelCalls calls,
            final PassTally tally) {
        final String id = order.get(at);
        final SegmentRecord record = byId.get(id);
        final Segment source = inputs.source(id).orElse(null);
        final String masked = record == null ? null : record.maskedMachineTarget();
        if (record == null || source == null || masked == null) {
            return Result.ok(false);
        }
        final String previous = shown(at > 0 ? byId.get(order.get(at - 1)) : null);
        final String next = shown(at + 1 < order.size() ? byId.get(order.get(at + 1)) : null);
        log.debug("Neighbour check segmentId={} previous={} next={}", id, !previous.isEmpty(), !next.isEmpty());
        final Result<Optional<GateResult.Restored>> revised = call.checkAgainstNeighbours(
                inputs, source, masked, facts(inputs, source, at, order), previous, next, calls);
        if (revised.isErr()) {
            // One paragraph's failed call (a timeout, an unreadable reply) is no reason to lose the whole pass or the
            // book's export; only the person's stop ends it.
            final AppError error = Objects.requireNonNull(revised.error(), "error");
            if (error.code() == ErrorCode.cancelled) {
                return Result.err(error);
            }
            log.warn("Neighbour check segmentId={} skipped: the call failed code={}", id, error.code());
            return Result.ok(false);
        }
        final GateResult.Restored restored = Objects.requireNonNull(revised.data(), "revised")
                .filter(answer -> !answer.maskedForm().equals(masked))
                .orElse(null);
        if (restored == null) {
            log.debug("Neighbour check segmentId={} found nothing to change", id);
            return Result.ok(false);
        }
        return storeFix(inputs, record, masked, restored, tally);
    }

    private Result<Boolean> storeFix(
            final PassInputs inputs,
            final SegmentRecord record,
            final String masked,
            final GateResult.Restored restored,
            final PassTally tally) {
        final String id = record.segmentId();
        return writer.store(record, restored, List.of(), RevisionWriter.Change.NEIGHBOUR)
                .map(how -> {
                    log.debug("Neighbour check segmentId={} stored={}", id, how);
                    log.trace("Neighbour check segmentId={} before={} after={}", id, masked, restored.maskedForm());
                    if (how == RevisionWriter.Stored.MACHINE_TARGET) {
                        tally.neighbourFixed(id, inputs.locator(id));
                    }
                    return how == RevisionWriter.Stored.MACHINE_TARGET;
                });
    }

    private static String shown(@Nullable final SegmentRecord record) {
        if (record == null) {
            return "";
        }
        final String text = record.userTarget() != null ? record.userTarget() : record.machineTarget();
        return text == null ? "" : text.strip();
    }

    // The names and terms the paragraph and its neighbours hold, with the rendering the glossary gives each.
    private static String facts(final PassInputs inputs, final Segment source, final int at, final List<String> order) {
        final StringBuilder around = new StringBuilder(source.masked());
        for (final int step : new int[] {-1, 1}) {
            final int neighbour = at + step;
            if (neighbour >= 0 && neighbour < order.size()) {
                inputs.source(order.get(neighbour))
                        .ifPresent(other -> around.append(' ').append(other.masked()));
            }
        }
        final String text = around.toString();
        return inputs.glossary().stream()
                .filter(entry -> entry.target() != null && !entry.target().isBlank())
                .filter(entry -> TermMatch.occursIn(entry.term(), text))
                .limit(MAX_FACT_LINES)
                .map(NeighbourRevision::fact)
                .collect(Collectors.joining("\n"));
    }

    private static String fact(final GlossaryEntry entry) {
        final String gender = entry.gender() == Gender.UNKNOWN
                ? ""
                : ", " + entry.gender().name().toLowerCase(Locale.ROOT);
        return "- " + entry.term() + " → " + entry.target() + gender;
    }
}
