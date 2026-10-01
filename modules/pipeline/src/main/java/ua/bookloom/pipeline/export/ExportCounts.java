package ua.bookloom.pipeline.export;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.SourceFallback;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.review.ReviewCounting;

/**
 * What an export writes, counted from the stored records by the review desk's own rule, so the report and the review
 * tiles never disagree. Unlike the desk, which counts work left, the report counts what the file holds: a FLAGGED
 * record with no machine target is written in its source, so it is pending here.
 *
 * @param written ACCEPTED and REVISED records and the FLAGGED records with a machine target
 * @param pending PENDING records of translated kinds and FLAGGED records with no machine target
 * @param sourceKept records kept as source by choice
 * @param flaggedWritten FLAGGED records written with their machine target
 * @param autoAccepted records accepted on their draft or reuse without review
 * @param reviewed records a person acted on
 * @param bodySegments the book's body segments, each re-opened and verified before the book is published
 * @param keptVerbatim records written as they are because nothing in them needed translating; counted in
 *     {@code written}, never in {@code sourceKept}
 */
@Slf4j
record ExportCounts(
        int written,
        int pending,
        int sourceKept,
        int flaggedWritten,
        int autoAccepted,
        int reviewed,
        int bodySegments,
        int keptVerbatim) {

    /**
     * Counts a project's records against the book they describe.
     *
     * @param records the non-null stored records
     * @param keptKinds the non-null auxiliary kinds the brief keeps as source
     * @param book the non-null opened book, whose body segments the export verifies
     * @return the counts
     */
    static ExportCounts of(final List<SegmentRecord> records, final Set<SegmentKind> keptKinds, final Document book) {
        Objects.requireNonNull(book, "book");
        final ReviewCounts counts = ReviewCounting.count(records, keptKinds);
        final int flaggedWritten = counts.flagged() - counts.flaggedWithoutTarget();
        final int acceptedOrRevised = (int) records.stream()
                .filter(record -> !record.isKeptAsSource(keptKinds))
                .filter(record -> record.status() == SegmentStatus.ACCEPTED || record.status() == SegmentStatus.REVISED)
                .count();
        final int bodySegments = book.units().stream()
                .filter(unit -> !unit.isAuxiliary())
                .mapToInt(unit -> unit.segments().size())
                .sum();
        final ExportCounts result = new ExportCounts(
                acceptedOrRevised + flaggedWritten,
                counts.pending() + counts.flaggedWithoutTarget(),
                counts.sourceKept(),
                flaggedWritten,
                counts.autoAccepted(),
                counts.reviewed(),
                bodySegments,
                counts.keptVerbatim());
        log.debug(
                "Export counts {} from records={} flaggedWithoutTarget={}",
                result,
                records.size(),
                counts.flaggedWithoutTarget());
        return result;
    }

    /**
     * These counts with segments that had a target but were written in their source moved from written to pending.
     *
     * @param total how many segments were written in their source instead of their target
     * @param flagged how many of them were FLAGGED records written with a machine target until now
     * @return the adjusted counts
     */
    ExportCounts withSourceFallbacks(final int total, final int flagged) {
        return new ExportCounts(
                written - total,
                pending + total,
                sourceKept,
                flaggedWritten - flagged,
                autoAccepted,
                reviewed,
                bodySegments,
                keptVerbatim);
    }

    /**
     * The report of a finished export.
     *
     * @param destination the non-null written book
     * @param sideFiles the non-null side files written beside it
     * @param consistency the non-null summary of the consistency pass
     * @param sourceFallbacks the non-null segments written in their source for a broken translation
     * @return the report carrying these counts
     */
    ExportReport report(
            final Path destination,
            final List<Path> sideFiles,
            final ConsistencySummary consistency,
            final List<SourceFallback> sourceFallbacks) {
        return new ExportReport(
                destination,
                written,
                pending,
                sourceKept,
                flaggedWritten,
                autoAccepted,
                reviewed,
                sideFiles,
                bodySegments,
                consistency,
                keptVerbatim,
                sourceFallbacks);
    }
}
