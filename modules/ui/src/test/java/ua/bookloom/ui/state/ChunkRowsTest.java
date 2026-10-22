package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.state.ChunkFixtures.AT;
import static ua.bookloom.ui.state.ChunkFixtures.CHUNK_ONE;
import static ua.bookloom.ui.state.ChunkFixtures.CHUNK_TWO;
import static ua.bookloom.ui.state.ChunkFixtures.accepted;
import static ua.bookloom.ui.state.ChunkFixtures.draft;
import static ua.bookloom.ui.state.ChunkFixtures.id;
import static ua.bookloom.ui.state.ChunkFixtures.started;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.SegmentDrafted;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.ui.state.LiveRow.Stage;

/** The rows of the live card: the chunk being translated, each with its own marker, worked out from the run's events. */
class ChunkRowsTest {

    private final LiveCallState state = new LiveCallState();

    private List<LiveRow> rows() {
        return state.view(AT).rows();
    }

    private List<RowMarker> markers() {
        return rows().stream().map(row -> RowMarker.of(row, RunRow.TRANSLATING)).toList();
    }

    private void draftAndAcceptAll() {
        IntStream.rangeClosed(1, 4).forEach(n -> state.drafted(new SegmentDrafted(id(n), "Чернетка " + n, 0.9)));
        IntStream.rangeClosed(1, 4).forEach(n -> state.decided(accepted(n, SegmentPath.DRAFT)));
    }

    // IF a batch of four with one item missing read as four equal rows, THEN the person would never see which segment
    // the engine had to translate again alone.
    @Test
    void batchWithOneMissingItem_marksThatRowAloneAndTheOthersTranslated() {
        state.snapshot(ChunkFixtures.batchWithMissingThird());
        state.snapshot(draft(2, CallKind.DRAFT, 3).answered("{}", null, Duration.ofSeconds(2)));
        draftAndAcceptAll();

        assertThat(markers())
                .containsExactly(RowMarker.TRANSLATED, RowMarker.TRANSLATED, RowMarker.ALONE, RowMarker.TRANSLATED);
        assertThat(rows().get(2).fallbackCode()).isEqualTo("MISSING");
        assertThat(rows().stream().map(LiveRow::locator).toList())
                .containsExactly("ch7 · p41", "ch7 · p42", "ch7 · p43", "ch7 · p44");
    }

    // IF the marker waited for the chunk's check, THEN the fallback would read "translating…" until the end of it.
    @Test
    void fellBackRow_draftedButNotYetDecided_alreadyReadsAlone() {
        state.snapshot(ChunkFixtures.batchWithMissingThird());
        state.drafted(new SegmentDrafted(id(3), "Чернетка 3", 0.9));

        assertThat(RowMarker.of(rows().get(2), RunRow.TRANSLATING)).isEqualTo(RowMarker.ALONE);
    }

    // IF a directed fix of one paragraph replaced the rows with its own, THEN the chunk would vanish from the card.
    @Test
    void directedFixOfOneSegment_keepsTheChunksFourRows() {
        state.snapshot(ChunkFixtures.batchWithMissingThird());

        state.snapshot(draft(3, CallKind.DIRECTED_FIX, 2));

        assertThat(rows()).hasSize(4);
        assertThat(rows().get(1).callId()).isEqualTo(3L);
    }

    // IF a paragraph kept as it is read "translated", THEN the person would look for a translation that is not there.
    @Test
    void verbatimSegment_readsKeptAsIs() {
        state.snapshot(ChunkFixtures.batchWithMissingThird());

        state.decided(accepted(3, SegmentPath.VERBATIM));

        assertThat(rows().get(2).stage()).isEqualTo(Stage.KEPT);
        assertThat(RowMarker.of(rows().get(2), RunRow.TRANSLATING)).isEqualTo(RowMarker.KEPT);
    }

    // IF a flagged segment read "translated", THEN the person would not know it waits for their review.
    @Test
    void flaggedSegment_readsToReview_andKeepsItsFindings() {
        state.snapshot(ChunkFixtures.batchWithMissingThird());

        state.decided(ChunkFixtures.flagged(2));

        assertThat(RowMarker.of(rows().get(1), RunRow.TRANSLATING)).isEqualTo(RowMarker.TO_REVIEW);
        assertThat(rows().get(1).findingKinds()).containsExactly("meaning");
        assertThat(rows().get(1).target()).isEqualTo("Погано 2");
    }

    // IF a draft call did not mark its segments, THEN a waiting call would show four rows doing nothing.
    @Test
    void waitingDraftCall_marksItsSegmentsInTheCallAndRecordsTheCallId() {
        state.snapshot(draft(1, CallKind.DRAFT, 1, 2));

        assertThat(rows()).extracting(LiveRow::stage).containsExactly(Stage.IN_CALL, Stage.IN_CALL);
        assertThat(rows()).extracting(LiveRow::callId).containsExactly(1L, 1L);
        assertThat(markers()).containsExactly(RowMarker.TRANSLATING, RowMarker.TRANSLATING);
    }

    // IF the next chunk's first segment did not clear the rows, THEN the card would list two chunks as one.
    @Test
    void startOfTheNextChunk_clearsTheRows() {
        state.started(started(1, CHUNK_ONE));
        state.started(started(2, CHUNK_ONE));
        assertThat(rows()).extracting(LiveRow::stage).containsExactly(Stage.QUEUED, Stage.QUEUED);

        state.started(started(3, CHUNK_TWO));

        assertThat(rows()).extracting(LiveRow::segmentId).containsExactly(id(3));
        assertThat(rows().get(0).displaySource()).isEqualTo("Source text 3");
    }

    // IF the rows grew without a bound, THEN a long section would hold every segment of the book in memory.
    @Test
    void rows_moreThanSixtyFour_keepTheNewestSixtyFour() {
        IntStream.rangeClosed(1, 70).forEach(n -> state.started(started(n, CHUNK_ONE)));

        assertThat(rows()).hasSize(64);
        assertThat(rows().get(0).segmentId()).isEqualTo(id(7));
    }
}
