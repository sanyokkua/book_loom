package ua.bookloom.pipeline.proofread;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.WholeWord;
import ua.bookloom.pipeline.glossary.NameForms;

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
     * What a gender deferral on the narrator's own words waits on, in place of a glossary character's term (15i.T5);
     * it cannot be a term, so no glossary entry is ever mistaken for the narrator.
     */
    public static final String NARRATOR = "⟦narrator⟧";

    /**
     * Everything a glossary write leaves behind on segments already decided: the TERM deferrals for a changed pinned
     * or locked rendering and the GENDER_CHANGED ones for a gender that was enforced meanwhile. Every writer of the
     * glossary calls this one method, so no path records one kind and misses the other (design D15). Nothing is
     * written; the caller stores what this returns.
     *
     * @param before the entry as it was; never null
     * @param after the entry as it is now; never null
     * @param decided the project's decided segments; never null
     * @param namingSegmentIds the ids of the segments whose source names the entry; never null, may be empty
     * @param targetLanguage the target language tag, for a pinned target's inflected forms; null when unknown
     * @return the term deferrals then the gender deferrals; never null, empty when the write changed neither
     */
    public static List<Deferral> changed(
            final GlossaryEntry before,
            final GlossaryEntry after,
            final List<SegmentRecord> decided,
            final Set<String> namingSegmentIds,
            @Nullable final String targetLanguage) {
        Objects.requireNonNull(namingSegmentIds, "namingSegmentIds");
        final List<Deferral> deferrals = new ArrayList<>(termChanged(before, after, decided, targetLanguage));
        deferrals.addAll(genderChanged(before, after, decided, namingSegmentIds));
        log.debug("Glossary change entry={} deferrals={}", after.id(), deferrals.size());
        return deferrals;
    }

    /**
     * Whether a write gives an entry an enforced gender after a state that was not that gender, which is when decided
     * segments may disagree with it. An entry that had no gender at all is not one: its waiting deferrals stay.
     *
     * @param before the entry as it was; never null
     * @param after the entry as it is now; never null
     * @return {@code true} if the gender is now the person's or the book's and it was known before as another value or
     *     as a value nobody had confirmed, {@code false} otherwise
     */
    public static boolean genderArrived(final GlossaryEntry before, final GlossaryEntry after) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        return after.hasConfirmedGender()
                && before.gender() != Gender.UNKNOWN
                && (!before.hasConfirmedGender() || before.gender() != after.gender());
    }

    /**
     * The TERM deferrals a glossary edit leaves on segments that were decided with the previous rendering.
     *
     * @param before the entry as it was; never null
     * @param after the entry as it is now; never null
     * @param decided the project's decided segments; never null
     * @param targetLanguage the target language tag whose name table inflects a pinned target; null when unknown
     * @return one deferral per decided segment whose effective target holds the previous target (or, for a pinned
     *     entry, one of its inflected forms) as a whole word; never null, empty when the entry is only suggested after
     *     the change, had no previous target, or kept its target
     */
    public static List<Deferral> termChanged(
            final GlossaryEntry before,
            final GlossaryEntry after,
            final List<SegmentRecord> decided,
            @Nullable final String targetLanguage) {
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
        final Pattern held = WholeWord.anyOf(previousForms(previous, before, after, targetLanguage));
        final List<Deferral> deferrals = decided.stream()
                .filter(record -> record.effectiveTarget()
                        .filter(text -> held.matcher(text).find())
                        .isPresent())
                .map(record -> termDeferral(after, record, previous))
                .toList();
        deferrals.forEach(DeferralRegister::logRecorded);
        return deferrals;
    }

    /**
     * {@link #termChanged(GlossaryEntry, GlossaryEntry, List, String)} with no inflected forms: a pinned target is
     * matched as written.
     *
     * @param before the entry as it was; never null
     * @param after the entry as it is now; never null
     * @param decided the project's decided segments; never null
     * @return the term deferrals; never null
     */
    public static List<Deferral> termChanged(
            final GlossaryEntry before, final GlossaryEntry after, final List<SegmentRecord> decided) {
        return termChanged(before, after, decided, null);
    }

    private static List<Deferral> genderChanged(
            final GlossaryEntry before,
            final GlossaryEntry after,
            final List<SegmentRecord> decided,
            final Set<String> namingSegmentIds) {
        if (!genderArrived(before, after)) {
            log.debug("Gender change entry={} recorded nothing: no enforced gender arrived", after.id());
            return List.of();
        }
        log.debug(
                "Gender change entry={} from={}/{} to={}/{} naming={}",
                after.id(),
                before.gender(),
                before.genderOrigin(),
                after.gender(),
                after.genderOrigin(),
                namingSegmentIds.size());
        final List<Deferral> deferrals = decided.stream()
                .filter(record -> namingSegmentIds.contains(record.segmentId()))
                .filter(record -> record.effectiveTarget().isPresent())
                .map(record ->
                        deferral(record.projectId(), record.segmentId(), DeferralReason.GENDER_CHANGED, after.term()))
                .toList();
        deferrals.forEach(DeferralRegister::logRecorded);
        return deferrals;
    }

    private static Set<String> previousForms(
            final String previous,
            final GlossaryEntry before,
            final GlossaryEntry after,
            @Nullable final String targetLanguage) {
        final Set<String> forms = new LinkedHashSet<>(List.of(previous));
        if (!after.locked() && targetLanguage != null) {
            forms.addAll(
                    NameForms.forms(previous, before.gender(), targetLanguage).all());
        }
        return forms;
    }

    /**
     * The GENDER_UNKNOWN deferrals a segment leaves: one per character it names that has no gender of any origin (a
     * likely or rule value records none, because nothing would ever settle it), because the words that agree with that
     * character may have to change once the gender is known.
     *
     * @param projectId the owning project's id; never null
     * @param segment the decided segment, whose display text is searched; never null
     * @param glossary the glossary the segment was drafted with; never null
     * @return one deferral per character entry with no gender whose term occurs whole-word in a body-text segment
     *     (a heading, title or metadata segment records none), in glossary order;
     *     never null, empty when there is none
     */
    public static List<Deferral> unknownGender(
            final String projectId, final Segment segment, final List<GlossaryEntry> glossary) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(glossary, "glossary");
        if (!isBodyText(segment.kind())) {
            log.debug("Unknown-gender check skipped segmentId={} kind={}", segment.id(), segment.kind());
            return List.of();
        }
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

    /**
     * The GENDER_UNKNOWN deferrals the segments holding the minority gender's forms of an inferred narrator leave: one
     * per segment, waiting on {@link #NARRATOR}, so backward revision re-renders them once the narrator's gender is
     * checkable (15i.T5).
     *
     * @param projectId the owning project's id; never null
     * @param segmentIds the ids of the segments whose narrator forms disagree with the inferred gender; never null
     * @return one deferral per segment id, in the given order; never null, empty when none is given
     */
    public static List<Deferral> narratorGender(final String projectId, final List<String> segmentIds) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentIds, "segmentIds");
        final List<Deferral> deferrals = segmentIds.stream()
                .map(segmentId -> deferral(projectId, segmentId, DeferralReason.GENDER_UNKNOWN, NARRATOR))
                .toList();
        log.debug("Narrator gender deferrals project={} segments={}", projectId, deferrals.size());
        return deferrals;
    }

    // A name in a heading, a title or the book's metadata is not a place its gender is agreed with.
    private static boolean isBodyText(final SegmentKind kind) {
        return switch (kind) {
            case PARAGRAPH, VERSE_LINE, LIST_ITEM, TABLE_CELL, FOOTNOTE, CAPTION -> true;
            case HEADING,
                    TITLE,
                    METADATA_TITLE,
                    METADATA_AUTHOR,
                    METADATA_DESCRIPTION,
                    FRONTMATTER_VALUE,
                    ALT,
                    NAV_LABEL -> false;
        };
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

    // What a deferral waits on is a name from the book, so it is TRACE-only (logging.md).
    private static void logRecorded(final Deferral deferral) {
        log.debug("Deferral recorded segmentId={} reason={}", deferral.segmentId(), deferral.reason());
        log.trace("Deferral recorded segmentId={} waitingOn={}", deferral.segmentId(), deferral.waitingOn());
    }

    private static Optional<String> notRecordedBecause(final String previous, final GlossaryEntry after) {
        if (after.isSuggested()) {
            return Optional.of("the entry is only suggested");
        }
        if (previous.isBlank()) {
            return Optional.of("the previous target is empty");
        }
        if (previous.equals(after.target())) {
            return Optional.of("the target is unchanged");
        }
        return Optional.empty();
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
