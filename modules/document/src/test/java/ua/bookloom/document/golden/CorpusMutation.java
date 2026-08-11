package ua.bookloom.document.golden;

import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;

/**
 * The corpus verification's P3 mutation: setting every segment's {@code targetInner} to a marker prefix plus its
 * own {@code sourceInner}, and the marker-strip check on the re-opened output — task 11.1a's load-bearing check,
 * which needs no comparator to be trustworthy (design.md D6).
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CorpusMutation {

    /** Cannot occur in real book text; deliberately exercises {@code TxtWriter}'s unrepresentable-character
     * refusal on a non-UTF-8 source, which the caller must record as a correct outcome, not a failure. */
    static final String MARKER = "⟪T⟫";

    /** The count of segments the marker-strip check found under the same id but with disagreeing stripped text,
     * and the count of P0 segment ids absent from the re-opened output entirely. */
    private record MarkerStrip(int mismatchCount, int missingCount) {}

    static CorpusMutationOutcome.Completed completedOutcomeOf(Document p0, Document beforeMutation, Document back) {
        final boolean countsMatch = back.units().size() == p0.units().size()
                && CorpusDocuments.segmentsOf(back).size()
                        == CorpusDocuments.segmentsOf(p0).size();
        final boolean tuplesMatch = CorpusDocuments.tuplesOf(back).equals(CorpusDocuments.tuplesOf(p0));
        final MarkerStrip strip = markerStripOf(beforeMutation, back);
        return new CorpusMutationOutcome.Completed(
                back.units().size(),
                CorpusDocuments.segmentsOf(back).size(),
                countsMatch,
                tuplesMatch,
                strip.mismatchCount() == 0 && strip.missingCount() == 0,
                strip.mismatchCount(),
                strip.missingCount());
    }

    private static MarkerStrip markerStripOf(Document beforeMutation, Document back) {
        final Map<String, String> reopenedById = CorpusDocuments.sourceById(back);
        int mismatch = 0;
        int missing = 0;
        for (final Segment original : CorpusDocuments.segmentsOf(beforeMutation)) {
            final String reopenedSource = reopenedById.get(original.id());
            if (reopenedSource == null) {
                missing++;
            } else if (!strippedEquals(reopenedSource, original.sourceInner())) {
                mismatch++;
            }
        }
        return new MarkerStrip(mismatch, missing);
    }

    private static boolean strippedEquals(String reopenedSource, String originalSourceInner) {
        return reopenedSource.startsWith(MARKER)
                && reopenedSource.substring(MARKER.length()).equals(originalSourceInner);
    }

    static Document withMarkerTargets(Document source) {
        final List<Unit> units =
                source.units().stream().map(CorpusMutation::withMarkerTargets).toList();
        return new Document(
                source.id(),
                source.format(),
                source.declaredLang(),
                source.detectedSourceLang(),
                source.charset(),
                source.hasBom(),
                source.contentHash(),
                source.metadata(),
                units);
    }

    private static Unit withMarkerTargets(Unit unit) {
        final List<Segment> segments =
                unit.segments().stream().map(CorpusMutation::withMarkerTarget).toList();
        return new Unit(unit.id(), unit.order(), unit.href(), unit.mediaType(), unit.skeleton(), segments);
    }

    private static Segment withMarkerTarget(Segment segment) {
        return new Segment(
                segment.id(),
                segment.unit(),
                segment.order(),
                segment.kind(),
                segment.sourceInner(),
                segment.masked(),
                segment.placeholders(),
                segment.sourceHash(),
                segment.prevKey(),
                segment.nextKey(),
                segment.anchor(),
                MARKER + segment.sourceInner(),
                segment.status(),
                segment.confidence());
    }
}
