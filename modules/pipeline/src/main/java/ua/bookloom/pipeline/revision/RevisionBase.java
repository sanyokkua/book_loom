package ua.bookloom.pipeline.revision;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.review.Proposals;

/**
 * The masked target a revision of one segment starts from. The term sweep and the gender revision share it so that a
 * person-edited segment is always revised from the proposal already waiting on it: the new proposal then holds every
 * earlier fix and supersedes the old one, and exactly one proposal waits on the segment.
 *
 * <p>Known limit, accepted as it is: a locked term renamed twice before the person applies the waiting proposal
 * ({@code Хейл} → {@code Гейл} → {@code Гаїл}) leaves the proposal holding the first new rendering, because the second
 * rename records no deferral on a segment whose own text never held {@code Гейл}; the person ignores the proposal or
 * edits by hand.
 *
 * @param masked the masked text the revision is applied to
 * @param proposal the open deferral carrying the waiting proposal the text came from, which the new one supersedes, or
 *     null when the text is the person's own or the machine target
 * @param read the stored record the text was taken from; the revision is stored only while the segment still reads so
 */
record RevisionBase(String masked, @Nullable Deferral proposal, SegmentRecord read) {

    /**
     * Picks the text a revision of {@code record} starts from.
     *
     * @param record the stored segment
     * @param open the project's open deferrals
     * @return the machine target for a machine-owned segment; for a person-edited one the waiting proposal, else the
     *     person's own text; null when that text has no masked form
     */
    static @Nullable RevisionBase of(final SegmentRecord record, final List<Deferral> open) {
        if (record.userTarget() == null) {
            final String machine = record.maskedMachineTarget();
            return machine == null ? null : new RevisionBase(machine, null, record);
        }
        final Optional<Deferral> proposal = Proposals.waitingOn(open, record.segmentId());
        final String masked = proposal.map(Deferral::maskedProposal).orElse(record.maskedUserTarget());
        return masked == null ? null : new RevisionBase(masked, proposal.orElse(null), record);
    }

    /**
     * The deferrals a new target answers: those it resolved, then the superseded proposal's carrier when there is one.
     *
     * @param resolved the non-empty deferrals the new target answers, the first of which will carry it as a proposal
     * @return never null; {@code resolved} followed by the superseded carrier unless it is already among them
     */
    List<Deferral> answering(final List<Deferral> resolved) {
        final Deferral superseded = proposal;
        if (superseded == null || resolved.contains(superseded)) {
            return List.copyOf(resolved);
        }
        return Stream.concat(resolved.stream(), Stream.of(superseded)).toList();
    }
}
