package ua.bookloom.pipeline.review;

import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.Deferral;

/**
 * Which open deferral holds a segment's pending backward-revision proposal, so the queries, Apply and a later
 * revision pass that builds on it all agree on it. At most one waits per segment: every revision of a person-edited
 * segment starts from the waiting proposal and supersedes it.
 */
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
}
