package ua.bookloom.pipeline.review;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.project.Deferral;

/**
 * Which open deferral holds a segment's pending backward-revision proposal, so the queries, Apply and a later
 * revision pass that builds on it all agree on it. At most one waits per segment: every revision of a person-edited
 * segment starts from the waiting proposal and supersedes it.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Proposals {

    /**
     * Finds the proposal waiting on a segment.
     *
     * @param open the non-null open deferrals of the project, in no particular order
     * @param segmentId the non-null segment id
     * @return the open deferral of that segment that holds a proposal in both forms, or empty when none does
     */
    public static Optional<Deferral> waitingOn(final List<Deferral> open, final String segmentId) {
        return open.stream()
                .filter(deferral -> deferral.segmentId().equals(segmentId))
                .filter(deferral -> deferral.proposal() != null && deferral.maskedProposal() != null)
                .findFirst();
    }

    /**
     * Withdraws the proposal waiting on a segment whose wording the person has just changed, since it was built on the
     * old wording. Its carrier is resolved and added again under the same id without the proposal — an open deferral is
     * never changed in place — so the deferral stays open and the next pass builds a new proposal from the new wording.
     *
     * @param deferrals the non-null deferral store
     * @param projectId the non-null project id
     * @param segmentId the non-null segment id
     * @return {@code true} if a proposal was withdrawn, {@code false} if none was waiting; or the storage error
     */
    public static Result<Boolean> withdraw(
            final DeferralRepository deferrals, final String projectId, final String segmentId) {
        Objects.requireNonNull(deferrals, "deferrals");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        return deferrals.open(projectId).flatMap(open -> withdrawFrom(deferrals, open, segmentId));
    }

    private static Result<Boolean> withdrawFrom(
            final DeferralRepository deferrals, final List<Deferral> open, final String segmentId) {
        final Optional<Deferral> waiting = waitingOn(open, segmentId);
        log.debug("Withdrawing a proposal segment={} proposalWaiting={}", segmentId, waiting.isPresent());
        if (waiting.isEmpty()) {
            return Result.ok(false);
        }
        final Deferral carrier = waiting.get();
        final Deferral withoutProposal = new Deferral(
                carrier.id(),
                carrier.projectId(),
                carrier.segmentId(),
                carrier.reason(),
                carrier.waitingOn(),
                carrier.replacedRendering(),
                null,
                null);
        return deferrals
                .resolve(carrier.projectId(), carrier.id())
                .flatMap(resolved -> deferrals.add(withoutProposal))
                .map(added -> {
                    log.debug(
                            "Withdrew the proposal segment={} deferral={} reason={}",
                            segmentId,
                            carrier.id(),
                            carrier.reason());
                    return true;
                });
    }
}
