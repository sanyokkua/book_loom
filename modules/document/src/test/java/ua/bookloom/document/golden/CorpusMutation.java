package ua.bookloom.document.golden;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
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

    private static final Pattern NAME_PART_OPEN = Pattern.compile("(<[a-z-]+>)");

    /** The count of segments the marker-strip check found under the same id but with disagreeing stripped text,
     * and the count of P0 segment ids absent from the re-opened output entirely. */
    private record MarkerStrip(int mismatchCount, int missingCount) {}

    static CorpusMutationOutcome.Completed completedOutcomeOf(Document p0, Document beforeMutation, Document back) {
        final boolean countsMatch = back.units().size() == p0.units().size()
                && CorpusDocuments.segmentsOf(back).size()
                        == CorpusDocuments.segmentsOf(p0).size();
        final boolean tuplesMatch =
                CorpusDocuments.tuplesWithoutAnchorOf(back).equals(CorpusDocuments.tuplesWithoutAnchorOf(p0));
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
            } else if (!strippedEquals(reopenedSource, original)) {
                mismatch++;
            }
        }
        return new MarkerStrip(mismatch, missing);
    }

    /**
     * Whether stripping the marker from the re-opened text restores the original. A multi-part author is written
     * only for what each part encloses (a marker outside the parts is discarded by design), so its marker sits
     * inside every part; any other segment gets the marker in front, plus one more inside each image alt text it
     * carries, because that alt text is its own auxiliary segment written into the same place.
     */
    private static boolean strippedEquals(String reopenedSource, Segment original) {
        final String source = original.sourceInner();
        if (isNamePartMarkup(original)) {
            return reopenedSource.equals(NAME_PART_OPEN.matcher(source).replaceAll("$1" + MARKER));
        }
        return reopenedSource.startsWith(MARKER)
                && reopenedSource.replace(MARKER, "").equals(source);
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

    /** An FB2 author is name-part markup; an EPUB creator is plain text and takes the marker in front. */
    private static boolean isNamePartMarkup(Segment segment) {
        return segment.kind() == SegmentKind.METADATA_AUTHOR
                && segment.sourceInner().startsWith("<");
    }

    private static String markedTargetOf(Segment segment) {
        return isNamePartMarkup(segment)
                ? NAME_PART_OPEN.matcher(segment.sourceInner()).replaceAll("$1" + MARKER)
                : MARKER + segment.sourceInner();
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
                markedTargetOf(segment),
                segment.status(),
                segment.confidence());
    }
}
