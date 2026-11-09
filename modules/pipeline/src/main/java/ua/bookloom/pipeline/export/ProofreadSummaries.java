package ua.bookloom.pipeline.export;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ProofreadSummary;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.ProofreadOutcome;
import ua.bookloom.api.project.ProofreadVerdict;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.proofread.GenderWaits;
import ua.bookloom.pipeline.proofread.Mark;
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
        final Set<String> marked = openMarks(book, records, open);
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

    /**
     * The segments that hold a mark no window has dealt with: never read, or read with the rewrite refused.
     *
     * @param book the non-null book and map as they are now
     * @param records the non-null stored records
     * @param open the non-null open deferrals
     * @return the ids in reading order; never null, empty when no mark is open
     */
    static Set<String> openMarks(
            final MarkList.Book book, final List<SegmentRecord> records, final List<Deferral> open) {
        final Map<String, SegmentRecord> byId = records.stream()
                .collect(Collectors.toMap(SegmentRecord::segmentId, Function.identity(), (first, second) -> first));
        return MarkList.of(book, records, open).stream()
                .map(Mark::segmentId)
                .filter(id -> isUnread(byId.get(id)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // Every mark is of a stored record, so a missing one never reaches here; it would count as unread.
    private static boolean isUnread(@Nullable final SegmentRecord record) {
        final ProofreadVerdict verdict = record == null ? null : record.proofread();
        return verdict == null || verdict.outcome() == ProofreadOutcome.REJECTED;
    }
}
