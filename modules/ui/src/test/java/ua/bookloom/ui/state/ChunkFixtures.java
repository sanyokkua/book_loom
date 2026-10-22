package ua.bookloom.ui.state;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.CallItem;
import ua.bookloom.api.pipeline.CallItemStatus;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.ChunkPosition;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDetail;
import ua.bookloom.api.pipeline.SegmentOutcomeNote;
import ua.bookloom.api.pipeline.SegmentStarted;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.ui.ProgressFixtures;

/** A chunk of four paragraphs (ch7 · p41 to p44) and the calls and decisions that go with it, for the row tests. */
final class ChunkFixtures {

    static final Instant AT = Instant.parse("2026-01-01T00:00:10Z");
    static final ChunkPosition CHUNK_ONE = new ChunkPosition(7, 11, 1, 3);
    static final ChunkPosition CHUNK_TWO = new ChunkPosition(7, 11, 2, 3);

    private ChunkFixtures() {}

    static String id(final int index) {
        return "s-" + index;
    }

    static CallSegment segment(final int index) {
        return new CallSegment(id(index), "ch7 · p" + (40 + index), "Source text " + index);
    }

    static SegmentStarted started(final int index, final ChunkPosition position) {
        return new SegmentStarted(id(index), "ch7 · p" + (40 + index), "Source text " + index, position);
    }

    /** A draft call about the numbered segments, waiting for the model. */
    static CallSnapshot draft(final long callId, final CallKind kind, final int... indexes) {
        return CallSnapshot.waiting(
                callId,
                kind,
                "Call " + callId + " · " + kind,
                CHUNK_ONE,
                java.util.Arrays.stream(indexes)
                        .mapToObj(ChunkFixtures::segment)
                        .toList(),
                List.of(),
                AT,
                1,
                2,
                Duration.ofSeconds(60));
    }

    /** The batch of four whose third item the reply lacked: the engine drafts it again alone. */
    static CallSnapshot batchWithMissingThird() {
        return draft(1, CallKind.DRAFT, 1, 2, 3, 4)
                .answered("{}", null, Duration.ofSeconds(5))
                .withItems(List.of(
                        new CallItem("1", id(1), "Перший.", CallItemStatus.OK, List.of()),
                        new CallItem("2", id(2), "Другий.", CallItemStatus.OK, List.of()),
                        new CallItem("3", id(3), null, CallItemStatus.MISSING, List.of()),
                        new CallItem("4", id(4), "Четвертий.", CallItemStatus.OK, List.of())))
                .withOutcome(new SegmentOutcomeNote(id(3), SegmentOutcomeNote.Kind.FELL_BACK, "MISSING"));
    }

    static SegmentDecided accepted(final int index, final SegmentPath path) {
        return new SegmentDecided(
                id(index),
                SegmentStatus.ACCEPTED,
                null,
                ProgressFixtures.progress(7, 11, index, 0, 4 - index),
                new SegmentDetail(null, path, List.of(), "Переклад " + index));
    }

    static SegmentDecided flagged(final int index) {
        return new SegmentDecided(
                id(index),
                SegmentStatus.FLAGGED,
                null,
                ProgressFixtures.progress(7, 11, 0, 1, 3),
                new SegmentDetail(0.2, SegmentPath.REPAIRED, List.of("meaning"), "Погано " + index));
    }

    static List<Integer> indexes(final int count) {
        return IntStream.rangeClosed(1, count).boxed().toList();
    }
}
