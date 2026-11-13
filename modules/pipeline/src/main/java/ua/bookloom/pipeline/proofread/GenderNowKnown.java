package ua.bookloom.pipeline.proofread;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.SegmentRecord;

/**
 * The segments a gender now known may have left wrong (design D15 "Fast"): an open gender deferral whose gender has
 * arrived and whose segment's text holds a word of the other gender. The Proofread stage's code part leaves such a
 * deferral open; at Fast no window reads it, so the review desk lists the segment in Worth a second look with the
 * reason {@code gender now known: <name> — <gender>}.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GenderNowKnown {

    /**
     * The mismatching segments and why.
     *
     * @param records the non-null stored records of the project
     * @param open the non-null open deferrals of the project
     * @param glossary the non-null glossary entries as they are now
     * @param narrator the non-null narrator of the brief as it is now
     * @param targetLanguage the non-null target language tag, whose gender check reads the text
     * @return the reason by segment id, in the order of the deferrals; never null, empty when nothing disagrees
     */
    public static Map<String, String> of(
            final List<SegmentRecord> records,
            final List<Deferral> open,
            final List<GlossaryEntry> glossary,
            final Narrator narrator,
            final String targetLanguage) {
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(open, "open");
        Objects.requireNonNull(narrator, "narrator");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        final Map<String, GlossaryEntry> byTerm = MarkList.byTerm(Objects.requireNonNull(glossary, "glossary"));
        final Map<String, SegmentRecord> byId = new LinkedHashMap<>();
        records.forEach(record -> byId.put(record.segmentId(), record));
        final Map<String, String> reasons = new LinkedHashMap<>();
        for (final Deferral deferral : open) {
            final Optional<ArrivedGender> arrived = ArrivedGender.of(deferral, byTerm, narrator);
            final SegmentRecord record = byId.get(deferral.segmentId());
            if (arrived.isPresent() && record != null && disagrees(record, arrived.get(), targetLanguage)) {
                reasons.putIfAbsent(deferral.segmentId(), arrived.get().reason());
            }
        }
        log.debug("Gender now known: deferrals={} mismatching segments={}", open.size(), reasons.size());
        return reasons;
    }

    /**
     * The segments whose open gender deferral now has its gender, whatever their text says: what the Export screen's
     * banner names as waiting on a fact the person has since given.
     *
     * @param open the non-null open deferrals of the project
     * @param glossary the non-null glossary entries as they are now
     * @param narrator the non-null narrator of the brief as it is now
     * @return the name of the arrived fact by segment id, in the order of the deferrals, the first fact of a segment
     *     winning; never null, empty when no fact arrived
     */
    public static Map<String, String> arrived(
            final List<Deferral> open, final List<GlossaryEntry> glossary, final Narrator narrator) {
        Objects.requireNonNull(open, "open");
        Objects.requireNonNull(narrator, "narrator");
        final Map<String, GlossaryEntry> byTerm = MarkList.byTerm(Objects.requireNonNull(glossary, "glossary"));
        final Map<String, String> names = new LinkedHashMap<>();
        open.forEach(deferral -> ArrivedGender.of(deferral, byTerm, narrator)
                .ifPresent(fact -> names.putIfAbsent(deferral.segmentId(), fact.name())));
        log.debug("Gender facts arrived: deferrals={} waiting segments={}", open.size(), names.size());
        return names;
    }

    private static boolean disagrees(
            final SegmentRecord record, final ArrivedGender arrived, final String targetLanguage) {
        return record.effectiveTarget()
                .filter(text -> arrived.mismatches(text, targetLanguage))
                .isPresent();
    }
}
