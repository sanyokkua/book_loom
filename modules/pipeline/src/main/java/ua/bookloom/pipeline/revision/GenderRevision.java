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
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.review.Proposals;

/**
 * The model half of backward revision: a segment that waits on characters whose gender is now known is re-rendered by
 * one revision call, however many of its characters are known, and a segment whose characters are all still unknown is
 * left alone. A person-edited segment is revised from the proposal already waiting on it, so a swept term and a gender
 * fix arrive in one proposal.
 */
@Slf4j
@RequiredArgsConstructor
final class GenderRevision {

    private final GlossaryRepository glossary;
    private final SegmentRepository segments;
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

    private Result<Boolean> reviseSegment(
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
            return Result.ok(false);
        }
        final Result<Optional<SegmentRecord>> found = segments.find(inputs.projectId(), segmentId);
        if (found.isErr()) {
            return Result.err(Objects.requireNonNull(found.error(), "error"));
        }
        final SegmentRecord record =
                Objects.requireNonNull(found.data(), "found").orElse(null);
        final Segment source = inputs.source(segmentId).orElse(null);
        final Base base = record == null ? null : base(record, open);
        if (base == null || source == null) {
            log.debug("Gender deferral left open segmentId={}: no stored target or no such segment", segmentId);
            return Result.ok(false);
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

    // A person-edited segment builds on the proposal already waiting on it, else on the person's own text.
    private static @Nullable Base base(final SegmentRecord record, final List<Deferral> open) {
        if (record.userTarget() == null) {
            final String machine = record.maskedMachineTarget();
            return machine == null ? null : new Base(machine, null);
        }
        final Optional<Deferral> proposal = Proposals.waitingOn(open, record.segmentId());
        final String masked = proposal.map(Deferral::maskedProposal).orElse(record.maskedUserTarget());
        return masked == null ? null : new Base(masked, proposal.orElse(null));
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

    private Result<Boolean> store(
            final PassInputs inputs,
            final String segmentId,
            final Base base,
            final List<Known> ready,
            final Optional<GateResult.Restored> revised,
            final PassTally tally) {
        if (revised.isEmpty()) {
            log.debug("Gender deferrals left open segmentId={}: the revision was not usable", segmentId);
            return Result.ok(false);
        }
        final List<Deferral> answered =
                new ArrayList<>(ready.stream().map(Known::deferral).toList());
        final Deferral superseded = base.proposal();
        if (superseded != null && !answered.contains(superseded)) {
            answered.add(superseded);
        }
        final GateResult.Restored restored = revised.get();
        return writer.store(inputs.projectId(), segmentId, restored, answered).map(how -> {
            log.debug("Gender revision segmentId={} characters={} stored={}", segmentId, ready.size(), how);
            log.trace(
                    "Gender revision segmentId={} before={} after={}", segmentId, base.masked(), restored.maskedForm());
            switch (how) {
                case MACHINE_TARGET -> tally.reRendered(segmentId, inputs.locator(segmentId));
                case PROPOSAL -> tally.proposed(segmentId, inputs.locator(segmentId));
            }
            return true;
        });
    }

    /** A gender deferral whose character now has a gender, with that character's entry. */
    private record Known(Deferral deferral, GlossaryEntry entry) {}

    /**
     * The target a revision starts from.
     *
     * @param masked the masked text sent as the one {@code <Text>} block
     * @param proposal the waiting proposal the text came from, which the new one supersedes, or null
     */
    private record Base(String masked, @Nullable Deferral proposal) {}
}
