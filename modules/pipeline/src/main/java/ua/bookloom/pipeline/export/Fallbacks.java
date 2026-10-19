package ua.bookloom.pipeline.export;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.SourceFallback;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.project.SegmentLocators;

/**
 * The segments an export wrote in their source although the run had worked on them, named for a person by their
 * locator: those whose translation broke the formatting are taken out of the written count, so the report never claims
 * a translation the file does not hold, and flagged segments with no target are only named, because the counts already
 * hold them as pending.
 *
 * @param opened the book the export decided its targets over
 * @param stored the project's stored records
 * @param counts the counts taken before any fallback was known
 */
@Slf4j
record Fallbacks(Document opened, List<SegmentRecord> stored, ExportCounts counts) {

    /**
     * The fallbacks' counts and list.
     *
     * @param counts the counts with every fallback moved from written to pending
     * @param listed every fallback in book order, each with its locator
     */
    record Fallen(ExportCounts counts, List<SourceFallback> listed) {}

    /** Rejects a missing component and copies the records. */
    Fallbacks {
        Objects.requireNonNull(opened, "opened");
        Objects.requireNonNull(counts, "counts");
        stored = List.copyOf(stored);
    }

    /**
     * Joins the fallbacks found before writing and on re-opening.
     *
     * @param beforeWriting the non-null ids whose stored targets no longer passed the placeholder gate
     * @param onReopening the non-null ids whose markup changed in the written book
     * @param noTarget the non-null ids of flagged segments with no target, already counted as pending
     * @return the adjusted counts and the named fallbacks, the broken ones first and the target-less ones after, each
     *     group in the order given
     */
    Fallen of(final List<String> beforeWriting, final List<String> onReopening, final List<String> noTarget) {
        final Set<String> ids = new LinkedHashSet<>(beforeWriting);
        ids.addAll(onReopening);
        final Map<String, SegmentLocator> locators = SegmentLocators.of(opened);
        final Map<String, SegmentRecord> byId = stored.stream()
                .collect(Collectors.toMap(SegmentRecord::segmentId, Function.identity(), (first, ignored) -> first));
        final List<SourceFallback> listed = Stream.concat(
                        ids.stream()
                                .map(id -> new SourceFallback(
                                        id, locatorOf(locators.get(id), id), SourceFallback.Reason.BROKEN_FORMATTING)),
                        noTarget.stream()
                                .filter(id -> !ids.contains(id))
                                .map(id -> new SourceFallback(
                                        id, locatorOf(locators.get(id), id), SourceFallback.Reason.NO_TARGET)))
                .toList();
        final int flagged = (int) ids.stream()
                .map(byId::get)
                .filter(record -> record != null && record.status() == SegmentStatus.FLAGGED)
                .count();
        if (!listed.isEmpty()) {
            log.info(
                    "Export wrote {} segments in their source: {} broken formatting, {} flagged with no target: {}",
                    listed.size(),
                    ids.size(),
                    listed.size() - ids.size(),
                    listed.stream().map(SourceFallback::locator).toList());
        }
        return new Fallen(counts.withSourceFallbacks(ids.size(), flagged), listed);
    }

    private static String locatorOf(@Nullable final SegmentLocator locator, final String id) {
        return locator == null ? id : locator.text();
    }
}
