package ua.bookloom.pipeline.proofread;

import static ua.bookloom.util.contract.ArgNames.ERROR;

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
 * The deterministic sweep of renamed glossary targets, run by the Proofread stage: each TERM deferral's previous rendering is replaced in the segment's masked form — whole-word by a locked
 * entry's current target, and form by form by the same case of a pinned entry's (a suggested entry is never swept) —
 * and the plain form is restored from it through the document gate, so both forms stay consistent and no tag or token
 * is touched. Every term deferral of one segment is swept into one new target, so two renamed terms in one sentence
 * both arrive. A person-edited segment is swept from the proposal already waiting on it, which the new one supersedes,
 * so an earlier fix is never lost and one proposal waits. No model call is made.
 */
@Slf4j
@RequiredArgsConstructor
public final class TermSweep {

    private final GlossaryRepository glossary;
    private final SegmentRepository segments;
    private final RevisionWriter writer;

    /**
     * What one sweep changed.
     *
     * @param substitutions how many term deferrals were swept into a machine target
     * @param proposals how many swept texts wait as a proposal on a segment the person edited
     */
    public record Swept(int substitutions, int proposals) {}

    /** Told of each segment the sweep changes, for a caller that keeps notes of them. */
    public interface Observer {

        /** An observer that is told nothing. */
        Observer NONE = new Observer() {
            @Override
            public void swept(final String segmentId, final int terms) {}

            @Override
            public void proposed(final String segmentId) {}
        };

        /** A machine target took {@code terms} term deferrals. */
        void swept(String segmentId, int terms);

        /** A swept text waits as a proposal on a segment the person edited. */
        void proposed(String segmentId);
    }

    /** The counts of one sweep as it goes; used from the sweep's own thread only. */
    private static final class Counts {

        private final Observer observer;
        private int substitutions;
        private int proposals;

        Counts(final Observer observer) {
            this.observer = observer;
        }

        void swept(final String segmentId, final int terms) {
            substitutions += terms;
            observer.swept(segmentId, terms);
        }

        void proposed(final String segmentId) {
            proposals++;
            observer.proposed(segmentId);
        }

        Swept done() {
            return new Swept(substitutions, proposals);
        }
    }

    /**
     * Sweeps every open TERM deferral.
     *
     * @param inputs the non-null run facts
     * @param open the non-null open deferrals of the project
     * @return what the sweep changed once every term deferral is swept or resolved, or the storage or gate error that
     *     stopped it
     */
    public Result<Swept> sweep(final SweepInputs inputs, final List<Deferral> open) {
        return sweep(inputs, open, Observer.NONE);
    }

    /**
     * Sweeps every open TERM deferral and tells {@code observer} of each segment it changes.
     *
     * @param inputs the non-null run facts
     * @param open the non-null open deferrals of the project
     * @param observer the non-null listener of the changes
     * @return as {@link #sweep(SweepInputs, List)}
     */
    public Result<Swept> sweep(final SweepInputs inputs, final List<Deferral> open, final Observer observer) {
        final Counts counts = new Counts(Objects.requireNonNull(observer, "observer"));
        final Map<String, List<Deferral>> bySegment = open.stream()
                .filter(deferral -> deferral.reason() == DeferralReason.TERM)
                .collect(Collectors.groupingBy(Deferral::segmentId, LinkedHashMap::new, Collectors.toList()));
        log.debug("Sweeping term deferrals project={} segments={}", inputs.projectId(), bySegment.size());
        for (final Map.Entry<String, List<Deferral>> segment : bySegment.entrySet()) {
            final Result<Boolean> swept = sweepSegment(inputs, segment.getKey(), segment.getValue(), open, counts);
            if (swept.isErr()) {
                return Result.err(Objects.requireNonNull(swept.error(), ERROR));
            }
        }
        return Result.ok(counts.done());
    }

    private Result<Boolean> sweepSegment(
            final SweepInputs inputs,
            final String segmentId,
            final List<Deferral> terms,
            final List<Deferral> open,
            final Counts tally) {
        final Result<Optional<SegmentRecord>> found = segments.find(inputs.projectId(), segmentId);
        if (found.isErr()) {
            return Result.err(Objects.requireNonNull(found.error(), ERROR));
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
        final Result<Sweep> applied = substitute(inputs, segmentId, base.masked(), pending);
        if (applied.isErr()) {
            return Result.err(Objects.requireNonNull(applied.error(), ERROR));
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
            final SweepInputs inputs, final String segmentId, final String masked, final List<Deferral> terms) {
        String working = masked;
        final List<Deferral> swept = new ArrayList<>();
        for (final Deferral term : terms) {
            final Result<Optional<String>> replaced = replaceOne(inputs, segmentId, working, term);
            if (replaced.isErr()) {
                return Result.err(Objects.requireNonNull(replaced.error(), ERROR));
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
            final SweepInputs inputs, final String segmentId, final String working, final Deferral term) {
        final String waitingOn = term.waitingOn();
        final String previous = term.replacedRendering();
        final Result<Optional<GlossaryEntry>> found =
                waitingOn == null ? Result.ok(Optional.empty()) : glossary.findByTerm(inputs.projectId(), waitingOn);
        if (found.isErr()) {
            return Result.err(Objects.requireNonNull(found.error(), ERROR));
        }
        final GlossaryEntry entry =
                Objects.requireNonNull(found.data(), "found").orElse(null);
        final Optional<String> reason = notSwept(entry, previous);
        if (reason.isPresent()) {
            return resolvedUnswept(segmentId, term, reason.get());
        }
        final GlossaryEntry swept = Objects.requireNonNull(entry, "entry");
        final Map<String, String> renamings =
                renamings(swept, Objects.requireNonNull(previous, "previous"), inputs.targetLanguage());
        final Matcher matcher = WholeWord.anyOf(renamings.keySet()).matcher(working);
        if (!matcher.find()) {
            return resolvedUnswept(segmentId, term, "the target no longer holds the old rendering");
        }
        log.debug(
                "Substituting a {} rendering segmentId={} forms={}", swept.targetState(), segmentId, renamings.size());
        log.trace("Substituting segmentId={} term={} renamings={}", segmentId, waitingOn, renamings);
        return Result.ok(
                Optional.of(matcher.replaceAll(match -> Matcher.quoteReplacement(renamings.get(match.group())))));
    }

    private Result<Optional<String>> resolvedUnswept(final String segmentId, final Deferral term, final String reason) {
        log.debug("Term deferral resolved unswept segmentId={}: {}", segmentId, reason);
        return writer.resolveAll(List.of(term)).map(done -> Optional.empty());
    }

    // A locked target is exact text, replaced as written; a pinned one declines, so each of its forms is replaced by
    // the same case of the new target.
    private static Map<String, String> renamings(
            final GlossaryEntry entry, final String previous, final String targetLanguage) {
        final String target = Objects.requireNonNull(entry.target(), "target");
        return entry.locked()
                ? Map.of(previous, target)
                : PinnedForms.renamings(previous, target, entry.gender(), targetLanguage);
    }

    private static Optional<String> notSwept(@Nullable final GlossaryEntry entry, @Nullable final String previous) {
        if (entry == null) {
            return Optional.of("the entry is gone");
        }
        if (entry.isSuggested()) {
            return Optional.of("the entry is only suggested");
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
            final SweepInputs inputs,
            final Segment source,
            final RevisionBase base,
            final Sweep sweep,
            final Counts tally) {
        final String candidate =
                WhitespaceRestoration.restore(source.masked(), sweep.masked().strip());
        return switch (inputs.gate().restore(source, candidate)) {
            case GateResult.Restored restored -> store(source.id(), base, restored, sweep, tally);
            case GateResult.GateFailed failed -> {
                log.debug(
                        "Swept target kept unstored segmentId={}: the gate refused it raisedBy={}",
                        source.id(),
                        failed.finding().raisedBy());
                yield Result.ok(false);
            }
            case GateResult.StepError(var error) -> Result.err(error);
        };
    }

    private Result<Boolean> store(
            final String segmentId,
            final RevisionBase base,
            final GateResult.Restored restored,
            final Sweep sweep,
            final Counts tally) {
        final Result<RevisionWriter.Stored> stored =
                writer.store(base.read(), restored, base.answering(sweep.swept()), RevisionWriter.Change.NAME_SWAP);
        if (stored.isErr()) {
            return Result.err(Objects.requireNonNull(stored.error(), ERROR));
        }
        final RevisionWriter.Stored how = Objects.requireNonNull(stored.data(), "stored");
        log.debug(
                "Term sweep segmentId={} terms={} stored={}",
                segmentId,
                sweep.swept().size(),
                how);
        log.trace("Term sweep segmentId={} before={} after={}", segmentId, base.masked(), restored.maskedForm());
        switch (how) {
            case MACHINE_TARGET -> tally.swept(segmentId, sweep.swept().size());
            case PROPOSAL -> {
                log.warn("Term sweep stored a proposal segmentId={}: the person edited the segment", segmentId);
                tally.proposed(segmentId);
            }
            // The sweep makes no model call, so only a truly concurrent action gets here; the next pass sweeps again.
            case STALE ->
                log.debug("Term deferrals left open segmentId={}: the segment changed as it was swept", segmentId);
        }
        return Result.ok(how != RevisionWriter.Stored.STALE);
    }

    /** A segment's masked target with its terms swept, and the deferrals that were. */
    private record Sweep(String masked, List<Deferral> swept) {}
}
