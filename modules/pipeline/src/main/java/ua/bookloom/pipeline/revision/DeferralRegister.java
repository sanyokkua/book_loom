package ua.bookloom.pipeline.revision;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.WholeWord;
import ua.bookloom.pipeline.judge.JudgeDeferral;

/**
 * Works out which deferrals a fact leaves behind, so backward revision fixes only what was recorded. Two changed
 * terms in one segment stay two deferrals because each is keyed by the term it waits on. Nothing here writes; the
 * caller stores what each producer returns.
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
        deferrals.forEach(DeferralRegister::logRecorded);
        return deferrals;
    }

    /**
     * The JUDGE deferrals the judge reported. They are recorded and shown, never resolved, so the judge's own words are
     * all they wait on.
     *
     * @param projectId the owning project's id; never null
     * @param judged the judge's deferrals, each on a segment id; never null
     * @return one deferral per judge deferral, in order; never null, empty when the judge reported none
     */
    public static List<Deferral> fromJudge(final String projectId, final List<JudgeDeferral> judged) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(judged, "judged");
        final List<Deferral> deferrals = judged.stream()
                .map(judge -> deferral(projectId, judge.segmentId(), DeferralReason.JUDGE, judge.reason()))
                .toList();
        deferrals.forEach(DeferralRegister::logRecorded);
        return deferrals;
    }

    /**
     * The GENDER_UNKNOWN deferrals a segment leaves: one per character it names whose gender nobody has set yet,
     * because the words that agree with that character may have to change once the gender is known.
     *
     * @param projectId the owning project's id; never null
     * @param segment the decided segment, whose display text is searched; never null
     * @param glossary the glossary the segment was drafted with; never null
     * @return one deferral per character entry of unknown gender whose term occurs whole-word, in glossary order;
     *     never null, empty when there is none
     */
    public static List<Deferral> unknownGender(
            final String projectId, final Segment segment, final List<GlossaryEntry> glossary) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(glossary, "glossary");
        final String text = DisplayText.of(segment.masked());
        final List<Deferral> deferrals = glossary.stream()
                .filter(entry -> entry.type() == TermType.CHARACTER && entry.gender() == Gender.UNKNOWN)
                .filter(entry -> !entry.term().isBlank())
                .filter(entry -> WholeWord.pattern(entry.term()).matcher(text).find())
                .map(entry -> deferral(projectId, segment.id(), DeferralReason.GENDER_UNKNOWN, entry.term()))
                .toList();
        log.debug(
                "Unknown-gender check segmentId={} glossaryEntries={} deferrals={}",
                segment.id(),
                glossary.size(),
                deferrals.size());
        deferrals.forEach(DeferralRegister::logRecorded);
        return deferrals;
    }

    private static Deferral deferral(
            final String projectId, final String segmentId, final DeferralReason reason, final String waitingOn) {
        return new Deferral(
                projectId + ":" + segmentId + ":" + reason.name() + ":" + waitingOn,
                projectId,
                segmentId,
                reason,
                waitingOn,
                null,
                null,
                null);
    }

    private static void logRecorded(final Deferral deferral) {
        log.debug(
                "Deferral recorded segmentId={} reason={} waitingOn={}",
                deferral.segmentId(),
                deferral.reason(),
                deferral.waitingOn());
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
