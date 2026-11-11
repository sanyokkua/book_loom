package ua.bookloom.pipeline.export;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ProofreadSummary;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.proofread.GenderWaits;
import ua.bookloom.pipeline.proofread.MarkList;

/**
 * The proofread summary an export keeps, read from the stored verdicts, findings and deferrals with no model call: a
 * written book never waits on a model, yet still says what the Proofread stage re-read and what waits on the person.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProofreadSummaries {

    /**
     * Counts what the Proofread stage re-read and what still waits.
     *
     * @param book the non-null book and map as they are now
     * @param records the non-null stored records
     * @param open the non-null open deferrals
     * @return the summary; never null
     */
    static ProofreadSummary of(final MarkList.Book book, final List<SegmentRecord> records, final List<Deferral> open) {
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(open, "open");
        final int reRead = (int)
                records.stream().filter(record -> record.proofread() != null).count();
        final Set<String> flagged = records.stream()
                .filter(record -> record.status() == SegmentStatus.FLAGGED)
                .map(SegmentRecord::segmentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        final Set<String> deferred = open.stream().map(Deferral::segmentId).collect(Collectors.toSet());
        final Set<String> marked = MarkList.openMarks(book, records, open);
        final Set<String> waiting = new LinkedHashSet<>(flagged);
        waiting.addAll(deferred);
        waiting.addAll(marked);
        final GenderWaits genders = GenderWaits.of(open, book.glossary());
        log.debug(
                "export proofread summary reRead={} waiting={} flagged={} deferred={} openMarks={}",
                reRead,
                waiting.size(),
                flagged.size(),
                deferred.size(),
                marked.size());
        return new ProofreadSummary(
                reRead, waiting.size(), genders.characters(), genders.suggestedSegments(), genders.unknownSegments());
    }
}
