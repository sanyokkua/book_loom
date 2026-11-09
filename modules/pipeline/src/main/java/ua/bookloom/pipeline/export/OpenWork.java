package ua.bookloom.pipeline.export;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.ExportOpenWork;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.proofread.GenderNowKnown;
import ua.bookloom.pipeline.proofread.MarkList;

/** The open work the Export screen's banners name, read from the stores before anything is written. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OpenWork {

    /**
     * Reads the open marks and the segments that wait on a fact the person has since given.
     *
     * @param book the non-null book and map as they are now
     * @param records the non-null stored records
     * @param open the non-null open deferrals
     * @return the open work; never null
     */
    static ExportOpenWork of(final MarkList.Book book, final List<SegmentRecord> records, final List<Deferral> open) {
        Objects.requireNonNull(book, "book");
        final int marks = ProofreadSummaries.openMarks(book, records, open).size();
        final Map<String, String> arrived =
                GenderNowKnown.arrived(open, book.glossary(), book.brief().narrator());
        final List<String> facts = List.copyOf(new LinkedHashSet<>(arrived.values()));
        log.debug("export open work openMarks={} waitingOnFact={} facts={}", marks, arrived.size(), facts.size());
        return new ExportOpenWork(marks, arrived.size(), facts);
    }
}
