package ua.bookloom.pipeline.memory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;

/** Builds segments and their decided records, and reads the summaries a keeper answered with. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RollingSummaryFixtures {

    static final String PROJECT = "p1";

    static Segment segment(final String unit, final int order, final SegmentKind kind, final String masked) {
        return new Segment(
                unit + ":" + order,
                unit,
                order,
                kind,
                masked,
                masked,
                Map.of(),
                "h" + order,
                null,
                null,
                new ByteSpanAnchor(0, masked.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    static SegmentRecord record(final Segment segment, final SegmentStatus status, final String maskedTarget) {
        return new SegmentRecord(
                PROJECT,
                segment.id(),
                segment.unit(),
                segment.order(),
                segment.kind(),
                status,
                maskedTarget,
                maskedTarget,
                null,
                null,
                0.9,
                null,
                List.of(),
                SegmentPath.DRAFT,
                0,
                false,
                null);
    }

    /** Decides {@code count} accepted paragraphs {@code Sentence N.} of {@code unit}, numbered from {@code from}. */
    static List<Result<Optional<RollingSummary>>> decideAccepted(
            final RollingSummaryKeeper keeper, final String unit, final int from, final int count) {
        return IntStream.range(from, from + count)
                .mapToObj(order -> decide(keeper, unit, order, SegmentStatus.ACCEPTED))
                .toList();
    }

    static Result<Optional<RollingSummary>> decide(
            final RollingSummaryKeeper keeper, final String unit, final int order, final SegmentStatus status) {
        final Segment segment = segment(unit, order, SegmentKind.PARAGRAPH, "Sentence " + order + ".");
        return keeper.onDecided(segment, record(segment, status, "Речення " + order + "."));
    }

    static List<Integer> versions(final List<Result<Optional<RollingSummary>>> results) {
        return results.stream()
                .map(result -> Objects.requireNonNull(result.data(), "data"))
                .flatMap(Optional::stream)
                .map(RollingSummary::version)
                .toList();
    }
}
