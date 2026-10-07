package ua.bookloom.pipeline.revision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * The model half of backward revision: a segment that waits on characters whose gender is now known is re-rendered by
 * one revision call, however many of its characters are known, and a segment whose characters are all still unknown is
 * left alone. A person-edited segment is revised from the proposal already waiting on it, so a swept term and a gender
 * fix arrive in one proposal. An answer for a segment that changed while its call waited is discarded and the call is
 * made once more from the segment as it reads now.
 */
@Slf4j
@RequiredArgsConstructor
final class GenderRevision {

    private final GlossaryRepository glossary;
    private final SegmentRepository segments;
    private final DeferralRepository deferrals;
    private final RevisionWriter writer;
    private final RevisionCall revisionCall;

    /**
     * Re-renders every segment with a gender deferral whose character now has a gender.
     *
     * @param inputs what the pass read as it started
     * @param open the project's open deferrals after the term sweep
     * @param calls the seam every revision call is sent through
     * @param tally where each change is counted
     * @return ok once every such segment is revised or left, or the error that ended the pass
     */
    Result<Boolean> revise(
            final PassInputs inputs, final List<Deferral> open, final ModelCalls calls, final PassTally tally) {
        final Map<String, List<Deferral>> bySegment = genderDeferrals(open);
        log.debug("Revising gender deferrals project={} segments={}", inputs.projectId(), bySegment.size());
        calls.planned(ModelCalls.Stage.GENDER_RETRY, bySegment.size());
        for (final Map.Entry<String, List<Deferral>> segment : bySegment.entrySet()) {
            final Result<Boolean> revised =
                    reviseSegment(inputs, segment.getKey(), segment.getValue(), open, calls, tally);
            if (revised.isErr()) {
                return revised;
            }
        }
        return Result.ok(true);
    }

    static Map<String, List<Deferral>> genderDeferrals(final List<Deferral> open) {
        return open.stream()
                .filter(deferral -> deferral.reason() == DeferralReason.GENDER_UNKNOWN)
                .collect(Collectors.groupingBy(Deferral::segmentId, LinkedHashMap::new, Collectors.toList()));
    }

    // A call can wait through a pause while the person reverts, retries or edits the segment; its answer is then for
    // text that is gone, so the call is made once more from the segment as it reads now, and never a third time.
    private Result<Boolean> reviseSegment(
            final PassInputs inputs,
            final String segmentId,
            final List<Deferral> waiting,
            final List<Deferral> open,
            final ModelCalls calls,
            final PassTally tally) {
        final Result<Optional<RevisionWriter.Stored>> first =
                reviseOnce(inputs, segmentId, waiting, open, calls, tally);
        if (!isStale(first)) {
            return first.map(Optional::isPresent);
        }
        log.debug("Gender revision redone segmentId={}: the segment changed while its call waited", segmentId);
        return deferrals
                .open(inputs.projectId())
                .flatMap(fresh -> reviseOnce(
                        inputs,
                        segmentId,
                        genderDeferrals(fresh).getOrDefault(segmentId, List.of()),
                        fresh,
                        calls,
                        tally))
                .map(second -> leftOpenIfStale(segmentId, second));
    }

    private static boolean isStale(final Result<Optional<RevisionWriter.Stored>> attempt) {
        final Optional<RevisionWriter.Stored> stored = attempt.data();
        return stored != null
                && stored.filter(how -> how == RevisionWriter.Stored.STALE).isPresent();
    }

    private static boolean leftOpenIfStale(final String segmentId, final Optional<RevisionWriter.Stored> stored) {
        final boolean stale =
                stored.filter(how -> how == RevisionWriter.Stored.STALE).isPresent();
        if (stale) {
            log.debug(
                    "Gender deferrals left open segmentId={}: the segment changed again during the redone call",
                    segmentId);
        }
        return stored.isPresent() && !stale;
    }

    /** One revision of the segment: how it was stored, or empty when nothing was sent or the answer was unusable. */
    private Result<Optional<RevisionWriter.Stored>> reviseOnce(
            final PassInputs inputs,
            final String segmentId,
            final List<Deferral> waiting,
            final List<Deferral> open,
            final ModelCalls calls,
            final PassTally tally) {
        final Result<List<Known>> known = known(inputs.projectId(), segmentId, waiting);
        if (known.isErr()) {
            return Result.err(Objects.requireNonNull(known.error(), "error"));
        }
        final List<Known> ready = Objects.requireNonNull(known.data(), "known");
        if (ready.isEmpty()) {
            return Result.ok(Optional.empty());
        }
        final Result<Optional<SegmentRecord>> found = segments.find(inputs.projectId(), segmentId);
        if (found.isErr()) {
            return Result.err(Objects.requireNonNull(found.error(), "error"));
        }
        final SegmentRecord record =
                Objects.requireNonNull(found.data(), "found").orElse(null);
        final Segment source = inputs.source(segmentId).orElse(null);
        final RevisionBase base = record == null ? null : RevisionBase.of(record, open);
        if (base == null || source == null) {
            log.debug("Gender deferral left open segmentId={}: no stored target or no such segment", segmentId);
            return Result.ok(Optional.empty());
        }
        return revisionCall
                .revise(inputs, source, base.masked(), facts(ready), calls)
                .flatMap(revised -> store(inputs, segmentId, base, ready, revised, tally));
    }

    private Result<List<Known>> known(final String projectId, final String segmentId, final List<Deferral> waiting) {
        final List<Known> ready = new ArrayList<>();
        for (final Deferral deferral : waiting) {
            final String character = deferral.waitingOn();
            final Result<Optional<GlossaryEntry>> found =
                    character == null ? Result.ok(Optional.empty()) : glossary.findByTerm(projectId, character);
            if (found.isErr()) {
                return Result.err(Objects.requireNonNull(found.error(), "error"));
            }
            final Optional<GlossaryEntry> known =
                    Objects.requireNonNull(found.data(), "found").filter(entry -> entry.gender() != Gender.UNKNOWN);
            log.debug("Gender deferral segmentId={} genderKnown={}", segmentId, known.isPresent());
            known.ifPresent(entry -> ready.add(new Known(deferral, entry)));
        }
        return Result.ok(ready);
    }

    private static String facts(final List<Known> ready) {
        return ready.stream().map(GenderRevision::fact).collect(Collectors.joining("\n"));
    }

    private static String fact(final Known known) {
        final GlossaryEntry entry = known.entry();
        final String target = entry.target();
        final String rendering = target == null || target.isBlank() ? "" : " (" + target + ")";
        return "- " + entry.term() + rendering + ": " + entry.gender().name().toLowerCase(Locale.ROOT);
    }

    private Result<Optional<RevisionWriter.Stored>> store(
            final PassInputs inputs,
            final String segmentId,
            final RevisionBase base,
            final List<Known> ready,
            final Optional<GateResult.Restored> revised,
            final PassTally tally) {
        if (revised.isEmpty()) {
            log.debug("Gender deferrals left open segmentId={}: the revision was not usable", segmentId);
            return Result.ok(Optional.empty());
        }
        final List<Deferral> answered =
                base.answering(ready.stream().map(Known::deferral).toList());
        final GateResult.Restored restored = revised.get();
        return writer.store(base.read(), restored, answered, RevisionWriter.Change.GENDER)
                .map(how -> {
                    log.debug("Gender revision segmentId={} characters={} stored={}", segmentId, ready.size(), how);
                    log.trace(
                            "Gender revision segmentId={} before={} after={}",
                            segmentId,
                            base.masked(),
                            restored.maskedForm());
                    switch (how) {
                        case MACHINE_TARGET -> tally.reRendered(segmentId, inputs.locator(segmentId));
                        case PROPOSAL -> tally.proposed(segmentId, inputs.locator(segmentId));
                        case STALE ->
                            log.trace("Gender revision segmentId={} discarded={}", segmentId, restored.maskedForm());
                    }
                    return Optional.of(how);
                });
    }

    /** A gender deferral whose character now has a gender, with that character's entry. */
    private record Known(Deferral deferral, GlossaryEntry entry) {}
}
