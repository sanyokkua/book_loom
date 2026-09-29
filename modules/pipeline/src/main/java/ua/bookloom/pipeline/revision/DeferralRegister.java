package ua.bookloom.pipeline.revision;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.WholeWord;

/**
 * Works out which deferrals a fact leaves behind, so backward revision fixes only what was recorded. Two changed
 * terms in one segment stay two deferrals because each is keyed by the term it waits on.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DeferralRegister {

    /**
     * The TERM deferrals a glossary edit leaves on segments that were decided with the previous rendering. Nothing is
     * written; the caller stores what this returns.
     *
     * @param before the entry as it was; never null
     * @param after the entry as it is now; never null
     * @param decided the project's decided segments; never null
     * @return one deferral per decided segment whose effective target holds the previous target as a whole word;
     *     never null, empty when the entry is not locked after the change, had no previous target, or kept its target
     */
    public static List<Deferral> termChanged(
            final GlossaryEntry before, final GlossaryEntry after, final List<SegmentRecord> decided) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(decided, "decided");
        log.debug("Term change term={} decidedSegments={}", after.term(), decided.size());
        final String beforeTarget = before.target();
        final String previous = beforeTarget == null ? "" : beforeTarget;
        final Optional<String> reasonNotRecorded = notRecordedBecause(previous, after);
        if (reasonNotRecorded.isPresent()) {
            log.debug("Term change term={} recorded nothing: {}", after.term(), reasonNotRecorded.get());
            return List.of();
        }
        final List<Deferral> deferrals = decided.stream()
                .filter(record -> holdsWholeWord(record, previous))
                .map(record -> termDeferral(after, record, previous))
                .toList();
        deferrals.forEach(d -> log.debug(
                "Deferral recorded segmentId={} reason={} waitingOn={}", d.segmentId(), d.reason(), d.waitingOn()));
        return deferrals;
    }

    private static Optional<String> notRecordedBecause(final String previous, final GlossaryEntry after) {
        if (!after.locked()) {
            return Optional.of("the entry is not locked");
        }
        if (previous.isBlank()) {
            return Optional.of("the previous target is empty");
        }
        if (previous.equals(after.target())) {
            return Optional.of("the target is unchanged");
        }
        return Optional.empty();
    }

    private static boolean holdsWholeWord(final SegmentRecord record, final String previous) {
        final Optional<String> target = record.effectiveTarget();
        return target.isPresent()
                && WholeWord.pattern(previous).matcher(target.get()).find();
    }

    private static Deferral termDeferral(final GlossaryEntry after, final SegmentRecord record, final String previous) {
        return new Deferral(
                record.projectId() + ":" + record.segmentId() + ":" + DeferralReason.TERM.name() + ":" + after.term(),
                record.projectId(),
                record.segmentId(),
                DeferralReason.TERM,
                after.term(),
                previous,
                null,
                null);
    }
}
