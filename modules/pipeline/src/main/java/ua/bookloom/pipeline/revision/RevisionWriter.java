package ua.bookloom.pipeline.revision;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.heal.GateResult;

/**
 * Stores one revised target: a machine-owned segment takes it as its machine target in both forms; a segment the
 * person edited keeps their text and gets it as a proposal in both forms instead, so an accepted proposal can be edited
 * again with its placeholders. The segment is checked and written in one step against the record the revision was
 * built from: if the person reverted, retried or edited it meanwhile — a revision call may wait through a whole pause —
 * the answer was written for text that is gone, so nothing is stored and the person's action stands.
 */
@Slf4j
@RequiredArgsConstructor
final class RevisionWriter {

    private final SegmentRepository segments;
    private final DeferralRepository deferrals;

    /** How a revised target was stored. */
    enum Stored {
        /** The machine target was replaced; the segment is REVISED, or still FLAGGED when a finding remains unfixed. */
        MACHINE_TARGET,

        /** The person's edit was kept and the target waits as a proposal. */
        PROPOSAL,

        /** The segment changed after the revision was built, so the answer was discarded and nothing was stored. */
        STALE
    }

    /**
     * What a revision changes, and so which findings recorded against a FLAGGED segment it can be said to fix: a name
     * swap must never hide a real problem, so a FLAGGED segment becomes REVISED only when every finding it carries is
     * one of these.
     */
    enum Change {
        /**
         * A renamed locked term swept in. It fixes a {@code glossary} finding: a finding does not name its term, so
         * every glossary finding on the segment is taken to be about the swapped name.
         */
        NAME_SWAP(true),

        /** A re-render for a character's now-known gender. Agreement is never a recorded finding, so it fixes none. */
        GENDER(false),

        /** A fix made against the neighbouring paragraphs. It is a repair of unnamed defects, so it fixes no finding. */
        NEIGHBOUR(false);

        private static final String GLOSSARY_KIND = "glossary";

        private final boolean fixesGlossaryFindings;

        Change(final boolean fixesGlossaryFindings) {
            this.fixesGlossaryFindings = fixesGlossaryFindings;
        }

        /** Whether this change fixes every one of {@code findings}; a segment flagged with none has nothing fixed. */
        boolean canFixAll(final List<QaFinding> findings) {
            return fixesGlossaryFindings
                    && !findings.isEmpty()
                    && findings.stream().allMatch(finding -> GLOSSARY_KIND.equals(finding.kind()));
        }
    }

    /**
     * Stores {@code revised} for its segment and resolves the deferrals it answers.
     *
     * @param base the segment as it was read when the revision was built; the write happens only if it still reads so
     * @param revised the new target, restored through the document gate
     * @param answered the open deferrals the new target answers, the first of which carries a proposal; not empty
     * @param change what the revision changed, which decides the status of a FLAGGED segment
     * @return how the target was stored — {@link Stored#STALE} when the segment changed meanwhile, with every
     *     deferral left open — or the storage error
     */
    Result<Stored> store(
            final SegmentRecord base,
            final GateResult.Restored revised,
            final List<Deferral> answered,
            final Change change) {
        final AtomicReference<Stored> how = new AtomicReference<>(Stored.STALE);
        final Result<SegmentRecord> updated = segments.update(
                base.projectId(), base.segmentId(), current -> written(base, current, revised, change, how));
        if (updated.isErr()) {
            return Result.err(Objects.requireNonNull(updated.error(), "error"));
        }
        final SegmentRecord after = Objects.requireNonNull(updated.data(), "updated");
        final Stored stored = Objects.requireNonNull(how.get(), "how");
        log.debug(
                "Storing a revised target segmentId={} change={} stored={} answers={}",
                base.segmentId(),
                change,
                stored,
                answered.size());
        return switch (stored) {
            case STALE -> discarded(base);
            case PROPOSAL -> propose(revised, answered);
            case MACHINE_TARGET -> {
                logStatus(base, after, change);
                yield resolveAll(answered).map(done -> Stored.MACHINE_TARGET);
            }
        };
    }

    // Runs inside the repository's one-record update, so the check and the write see the same record.
    private static SegmentRecord written(
            final SegmentRecord base,
            final SegmentRecord current,
            final GateResult.Restored revised,
            final Change change,
            final AtomicReference<Stored> how) {
        if (!readsAs(base, current)) {
            return current;
        }
        if (current.userTarget() != null) {
            how.set(Stored.PROPOSAL);
            return current;
        }
        how.set(Stored.MACHINE_TARGET);
        final boolean keepsFlag = current.status() == SegmentStatus.FLAGGED && !change.canFixAll(current.findings());
        return current.withMachineTarget(revised.restored(), revised.maskedForm())
                .withStatus(keepsFlag ? SegmentStatus.FLAGGED : SegmentStatus.REVISED);
    }

    private static boolean readsAs(final SegmentRecord base, final SegmentRecord current) {
        return base.status() == current.status()
                && Objects.equals(base.machineTarget(), current.machineTarget())
                && Objects.equals(base.maskedMachineTarget(), current.maskedMachineTarget())
                && Objects.equals(base.userTarget(), current.userTarget())
                && Objects.equals(base.maskedUserTarget(), current.maskedUserTarget());
    }

    private static Result<Stored> discarded(final SegmentRecord base) {
        log.debug(
                "Revised answer discarded segmentId={}: the segment changed after the revision was built from it",
                base.segmentId());
        return Result.ok(Stored.STALE);
    }

    private static void logStatus(final SegmentRecord base, final SegmentRecord after, final Change change) {
        if (base.status() == SegmentStatus.FLAGGED) {
            log.debug(
                    "Flagged segment revised segmentId={} change={} findings={} fixed={} status={}",
                    base.segmentId(),
                    change,
                    after.findings().stream().map(QaFinding::kind).toList(),
                    change.canFixAll(after.findings()),
                    after.status());
        }
    }

    // The carrier is resolved and added again with the proposal, since an open deferral is never changed in place.
    private Result<Stored> propose(final GateResult.Restored revised, final List<Deferral> answered) {
        if (answered.isEmpty()) {
            log.debug("A revision with no deferral to carry it is dropped: the segment is the person's now");
            return Result.ok(Stored.STALE);
        }
        final Deferral carrier = answered.getFirst();
        final Deferral withProposal = new Deferral(
                carrier.id(),
                carrier.projectId(),
                carrier.segmentId(),
                carrier.reason(),
                carrier.waitingOn(),
                carrier.replacedRendering(),
                revised.restored(),
                revised.maskedForm());
        return resolveAll(answered).flatMap(done -> deferrals.add(withProposal)).map(added -> Stored.PROPOSAL);
    }

    Result<Boolean> resolveAll(final List<Deferral> answered) {
        for (final Deferral deferral : answered) {
            final Result<Boolean> resolved = deferrals.resolve(deferral.projectId(), deferral.id());
            if (resolved.isErr()) {
                return resolved;
            }
        }
        return Result.ok(true);
    }
}
