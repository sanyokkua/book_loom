package ua.bookloom.document.golden;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.DocumentService;

/**
 * The corpus verification's mask probe (design.md D6, task 10.1): every segment of the opened document is given
 * its own masked form back as its own target, restored through the real {@code DocumentService#unmask} port, and
 * compared against its source content under the same canonical rule {@link MaskRestoreCanonicalAssert} uses for
 * the catalogue-wide sweep ({@code MaskThenRestoreIdentitySweepTest}) — plus the placeholder-cost statistics DD-49
 * asks a corpus run to measure rather than assume, since naive masking would explode the placeholder multiset on
 * real book text. {@code unmask} needs no document registry (it is a pure function of a format, a segment and a
 * target string), so this probe never re-opens the book the way the write-based probes must.
 *
 * <p><strong>Blocks skipped as code-only, not measured here.</strong> Task 4.4's XHTML-only exclusion in
 * {@code BlockSegmentWalker} is decided during that walker's own recursive wrapper-descent pass — an element is
 * skipped only when it is itself a {@code <code>} tag reached while its enclosing wrapper owns no direct text,
 * interleaved with run-splitting and the {@code pre}/{@code math} exclusions. Re-deriving that predicate here,
 * over a book re-parsed independently of the real walker, would duplicate intricate production logic that this
 * probe — excluded from CI (task 10.3) — could silently drift out of step with; nothing would notice until the
 * count was already wrong. {@link CorpusMaskOutcome.Completed#skippedCodeOnlyBlocks()} is therefore always
 * {@code null} (not-measured, not zero), reported that way rather than silently as {@code 0} so a report reader
 * cannot mistake "not measured" for "none found". A later change is free to populate it once the count is
 * load-bearing, ideally by asking the walker's own code path rather than a second implementation of it.
 *
 * <p><strong>The mask-time invariant.</strong> {@code MaskInvariantException} is thrown while a segment's masked
 * form is built — during {@code DocumentService#open}, not here. A book that trips it never reaches this probe:
 * {@code DocumentService#open}'s own boundary {@code catch (Throwable t)} already turns it into
 * {@code Result.err(AppError(internal, ...))}, which {@link CorpusSweep} already records as a P0
 * {@code CorpusOpenOutcome.Failed}. This probe carries no separate field for it.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CorpusMaskProbe {

    /**
     * Runs the probe over every segment of {@code document}.
     *
     * @param service the port to restore through; never null
     * @param document the already-opened document whose segments are probed; never null
     * @return the probe's outcome; never null
     */
    static CorpusMaskOutcome.Completed run(DocumentService service, Document document) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(document, "document");
        final List<Segment> segments = CorpusDocuments.segmentsOf(document);
        final PlaceholderStats stats = placeholderStatsOf(segments);
        final RestoreStats restore = restoreStatsOf(service, document.format(), segments);
        return new CorpusMaskOutcome.Completed(
                segments.size(),
                stats.total(),
                stats.max(),
                stats.withPlaceholders(),
                restore.mismatchCount() == 0,
                restore.mismatchCount(),
                null,
                restore.firstFailureMessage());
    }

    private record PlaceholderStats(int total, int max, int withPlaceholders) {}

    private static PlaceholderStats placeholderStatsOf(List<Segment> segments) {
        int total = 0;
        int max = 0;
        int withPlaceholders = 0;
        for (final Segment segment : segments) {
            final int count = segment.placeholders().size();
            total += count;
            max = Math.max(max, count);
            if (count > 0) {
                withPlaceholders++;
            }
        }
        return new PlaceholderStats(total, max, withPlaceholders);
    }

    private record RestoreStats(int mismatchCount, @Nullable String firstFailureMessage) {}

    /** Never throws: every segment's own restore-and-compare failure is caught and folded into the count, exactly
     * as {@link CorpusSweep#compareCanonical} does for the identity probe. */
    private static RestoreStats restoreStatsOf(DocumentService service, BookFormat format, List<Segment> segments) {
        int mismatch = 0;
        String firstFailure = null;
        for (final Segment segment : segments) {
            final String failure = restoreOneOrNull(service, format, segment);
            if (failure != null) {
                mismatch++;
                firstFailure = firstFailure == null ? failure : firstFailure;
            }
        }
        return new RestoreStats(mismatch, firstFailure);
    }

    private static @Nullable String restoreOneOrNull(DocumentService service, BookFormat format, Segment segment) {
        final Result<String> restored = service.unmask(format, segment, segment.masked());
        if (restored.isErr()) {
            return "segment " + segment.id() + " failed to restore its own masked form: "
                    + Objects.requireNonNull(restored.error()).code();
        }
        return matchesSourceOrNull(format, segment, Objects.requireNonNull(restored.data()));
    }

    private static @Nullable String matchesSourceOrNull(BookFormat format, Segment segment, String restored) {
        final String context = "segment " + segment.id();
        try {
            switch (format) {
                case TXT, MARKDOWN -> assertExactMatch(segment, restored, context);
                case EPUB ->
                    MaskRestoreCanonicalAssert.assertEpubFragmentCanonicalEqual(
                            segment.sourceInner(), restored, context);
                case FB2 ->
                    MaskRestoreCanonicalAssert.assertFb2FragmentCanonicalEqual(
                            segment.sourceInner(), restored, context);
            }
            return null;
        } catch (AssertionError mismatch) {
            return mismatch.getMessage();
        }
    }

    /** TXT and Markdown restore by exact-string equality (DD-43); thrown as an {@link AssertionError} so both
     * comparison shapes share the one catch site above. */
    private static void assertExactMatch(Segment segment, String restored, String context) {
        if (!restored.equals(segment.sourceInner())) {
            throw new AssertionError(context + " did not restore to its own source content");
        }
    }
}
