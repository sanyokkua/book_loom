package ua.bookloom.pipeline.revision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.WhitespaceRestoration;
import ua.bookloom.pipeline.WholeWord;
import ua.bookloom.pipeline.heal.GateResult;

/**
 * The deterministic half of backward revision: each TERM deferral's previous rendering is replaced, whole-word, by its
 * locked entry's current target in the segment's masked form, and the plain form is restored from it through the
 * document gate, so both forms stay consistent and no tag or token is touched. Every term deferral of one segment is
 * swept into one new target, so two renamed terms in one sentence both arrive. A person-edited segment is swept from
 * the proposal already waiting on it, which the new one supersedes, so an earlier fix is never lost and one proposal
 * waits. No model call is made.
 */
@Slf4j
@RequiredArgsConstructor
final class TermSweep {

    private final GlossaryRepository glossary;
    private final SegmentRepository segments;
    private final RevisionWriter writer;

    /**
     * Sweeps every open TERM deferral.
     *
     * @param inputs what the pass read as it started
     * @param open the project's open deferrals
     * @param tally where each change is counted
     * @return ok once every term deferral is swept or resolved, or the storage or gate error that stopped the sweep
     */
    Result<Boolean> sweep(final PassInputs inputs, final List<Deferral> open, final PassTally tally) {
        final Map<String, List<Deferral>> bySegment = open.stream()
                .filter(deferral -> deferral.reason() == DeferralReason.TERM)
                .collect(Collectors.groupingBy(Deferral::segmentId, LinkedHashMap::new, Collectors.toList()));
        log.debug("Sweeping term deferrals project={} segments={}", inputs.projectId(), bySegment.size());
        for (final Map.Entry<String, List<Deferral>> segment : bySegment.entrySet()) {
            final Result<Boolean> swept = sweepSegment(inputs, segment.getKey(), segment.getValue(), open, tally);
            if (swept.isErr()) {
                return swept;
            }
        }
        return Result.ok(true);
    }

    private Result<Boolean> sweepSegment(
            final PassInputs inputs,
            final String segmentId,
            final List<Deferral> terms,
            final List<Deferral> open,
            final PassTally tally) {
        final Result<Optional<SegmentRecord>> found = segments.find(inputs.projectId(), segmentId);
        if (found.isErr()) {
            return Result.err(Objects.requireNonNull(found.error(), "error"));
        }
        final SegmentRecord record =
                Objects.requireNonNull(found.data(), "found").orElse(null);
        final RevisionBase base = record == null ? null : RevisionBase.of(record, open);
        final Segment source = inputs.source(segmentId).orElse(null);
        final List<Deferral> pending = unswept(terms, base);
        if (base == null || source == null) {
            log.debug("Term deferrals resolved unswept segmentId={}: no stored target or no such segment", segmentId);
            return writer.resolveAll(pending);
        }
        final Result<Sweep> applied = substitute(inputs.projectId(), segmentId, base.masked(), pending);
        if (applied.isErr()) {
            return Result.err(Objects.requireNonNull(applied.error(), "error"));
        }
        final Sweep sweep = Objects.requireNonNull(applied.data(), "sweep");
        return sweep.swept().isEmpty() ? Result.ok(true) : restoreAndStore(inputs, source, base, sweep, tally);
    }

    // The deferral carrying the waiting proposal was swept into it already; sweeping it again would resolve it.
    private static List<Deferral> unswept(final List<Deferral> terms, @Nullable final RevisionBase base) {
        final Deferral carrier = base == null ? null : base.proposal();
        final List<Deferral> pending =
                terms.stream().filter(term -> !term.equals(carrier)).toList();
        log.debug(
                "Term deferrals to sweep terms={} pending={} fromProposal={}",
                terms.size(),
                pending.size(),
                carrier != null);
        return pending;
    }

    private Result<Sweep> substitute(
            final String projectId, final String segmentId, final String masked, final List<Deferral> terms) {
        String working = masked;
        final List<Deferral> swept = new ArrayList<>();
        for (final Deferral term : terms) {
            final Result<Optional<String>> replaced = replaceOne(projectId, segmentId, working, term);
            if (replaced.isErr()) {
                return Result.err(Objects.requireNonNull(replaced.error(), "error"));
            }
            final Optional<String> next = Objects.requireNonNull(replaced.data(), "replaced");
            if (next.isPresent()) {
                working = next.get();
                swept.add(term);
            }
        }
        return Result.ok(new Sweep(working, swept));
    }

    /** One term's substitution, or empty when the deferral was resolved instead: the answer is then already stored. */
    private Result<Optional<String>> replaceOne(
            final String projectId, final String segmentId, final String working, final Deferral term) {
        final String waitingOn = term.waitingOn();
        final String previous = term.replacedRendering();
        final Result<Optional<GlossaryEntry>> found =
                waitingOn == null ? Result.ok(Optional.empty()) : glossary.findByTerm(projectId, waitingOn);
        if (found.isErr()) {
            return Result.err(Objects.requireNonNull(found.error(), "error"));
        }
        final GlossaryEntry entry =
                Objects.requireNonNull(found.data(), "found").orElse(null);
        final Optional<String> reason = notSwept(entry, previous);
        if (reason.isPresent()) {
            log.debug("Term deferral resolved unswept segmentId={}: {}", segmentId, reason.get());
            return writer.resolveAll(List.of(term)).map(done -> Optional.empty());
        }
        final Matcher matcher =
                WholeWord.pattern(Objects.requireNonNull(previous, "previous")).matcher(working);
        if (!matcher.find()) {
            log.debug(
                    "Term deferral resolved unswept segmentId={}: the target no longer holds the old rendering",
                    segmentId);
            return writer.resolveAll(List.of(term)).map(done -> Optional.empty());
        }
        final String target =
                Objects.requireNonNull(Objects.requireNonNull(entry, "entry").target(), "target");
        log.debug("Substituting a locked rendering segmentId={}", segmentId);
        log.trace("Substituting segmentId={} term={} from={} to={}", segmentId, waitingOn, previous, target);
        return Result.ok(Optional.of(matcher.replaceAll(Matcher.quoteReplacement(target))));
    }

    private static Optional<String> notSwept(@Nullable final GlossaryEntry entry, @Nullable final String previous) {
        if (entry == null) {
            return Optional.of("the entry is gone");
        }
        if (!entry.locked()) {
            return Optional.of("the entry is not locked");
        }
        final String target = entry.target();
        if (target == null || target.isBlank()) {
            return Optional.of("the entry has no target");
        }
        if (previous == null || previous.isBlank()) {
            return Optional.of("no previous rendering is recorded");
        }
        return Optional.empty();
    }

    private Result<Boolean> restoreAndStore(
            final PassInputs inputs,
            final Segment source,
            final RevisionBase base,
            final Sweep sweep,
            final PassTally tally) {
        final String candidate =
                WhitespaceRestoration.restore(source.masked(), sweep.masked().strip());
        return switch (inputs.gate().restore(source, candidate)) {
            case GateResult.Restored restored -> store(inputs, source.id(), base, restored, sweep, tally);
            case GateResult.GateFailed failed -> {
                log.debug(
                        "Swept target kept unstored segmentId={}: the gate refused it raisedBy={}",
                        source.id(),
                        failed.finding().raisedBy());
                yield Result.ok(false);
            }
            case GateResult.StepError stepError -> Result.err(stepError.error());
        };
    }

    private Result<Boolean> store(
            final PassInputs inputs,
            final String segmentId,
            final RevisionBase base,
            final GateResult.Restored restored,
            final Sweep sweep,
            final PassTally tally) {
        final Result<RevisionWriter.Stored> stored =
                writer.store(base.read(), restored, base.answering(sweep.swept()), RevisionWriter.Change.NAME_SWAP);
        if (stored.isErr()) {
            return Result.err(Objects.requireNonNull(stored.error(), "error"));
        }
        final RevisionWriter.Stored how = Objects.requireNonNull(stored.data(), "stored");
        log.debug(
                "Term sweep segmentId={} terms={} stored={}",
                segmentId,
                sweep.swept().size(),
                how);
        log.trace("Term sweep segmentId={} before={} after={}", segmentId, base.masked(), restored.maskedForm());
        switch (how) {
            case MACHINE_TARGET ->
                tally.swept(segmentId, inputs.locator(segmentId), sweep.swept().size());
            case PROPOSAL -> tally.proposed(segmentId, inputs.locator(segmentId));
            // The sweep makes no model call, so only a truly concurrent action gets here; the next pass sweeps again.
            case STALE ->
                log.debug("Term deferrals left open segmentId={}: the segment changed as it was swept", segmentId);
        }
        return Result.ok(how != RevisionWriter.Stored.STALE);
    }

    /** A segment's masked target with its terms swept, and the deferrals that were. */
    private record Sweep(String masked, List<Deferral> swept) {}
}
