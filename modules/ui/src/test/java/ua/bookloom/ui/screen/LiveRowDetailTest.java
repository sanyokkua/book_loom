package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.LiveRowFixtures.row;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.ui.LiveCallFixtures;
import ua.bookloom.ui.LiveRowFixtures;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.LiveRow.Stage;

/** The per-row markers of the live card, and the detail of the selected row in Simple and in Detailed. */
class LiveRowDetailTest extends LiveCardTestBase {

    private static final String DECIDED = "Вона втратила матір.";

    // The call of four segments ch2 · p05 to p08, in flight, with the clock at 21 s of 3:00.
    private static CallSnapshot call42() {
        final List<CallSegment> segments = IntStream.rangeClosed(5, 8)
                .mapToObj(n -> new CallSegment("s-" + n, "ch2 · p0" + n, "Source text " + n))
                .toList();
        return CallSnapshot.waiting(
                42,
                CallKind.DRAFT,
                "draft-batch-json",
                null,
                segments,
                LiveCallFixtures.smallPrompt(),
                LiveCallFixtures.STARTED,
                1,
                2,
                Duration.ofMinutes(3));
    }

    private static List<LiveRow> mixedChunk() {
        return List.of(
                row(1, Stage.ACCEPTED, DECIDED),
                row(2, Stage.IN_CALL, null),
                row(3, Stage.QUEUED, null),
                row(4, Stage.FLAGGED, "Погано."));
    }

    // IF a fallback read like any other translation, THEN the person would not know the batch reply was lacking.
    @Test
    void rows_batchOfFourWithOneMissing_threeTranslatedAndOneTranslatedAgainAlone() {
        showRows(List.of(
                row(1, Stage.ACCEPTED, "Перший."),
                row(2, Stage.ACCEPTED, "Другий."),
                LiveRowFixtures.fellBack(3, "MISSING", "Третій."),
                row(4, Stage.ACCEPTED, "Четвертий.")));

        assertThat(markerWords()).containsExactly("translated", "translated", "translated again alone", "translated");
        assertThat(locators()).containsExactly("ch7 · p41", "ch7 · p42", "ch7 · p43", "ch7 · p44");
    }

    // IF the fallback's cause were not on hover, THEN "again alone" would be a mystery.
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            MISSING        | the batch reply lacked this id
            DUPLICATE      | the batch reply answered this id twice
            MERGED_SUSPECT | the batch reply ran this segment into the next one
            """)
    void aloneMarker_hovered_namesWhyTheBatchReplyWasNotUsed(final String code, final String why) {
        showRows(List.of(LiveRowFixtures.fellBack(3, code, "Третій.")));

        assertThat(TooltipProbe.tipText(cells().get(0).lookup(".state-marker"))).isEqualTo(why);
    }

    // IF the other rows wrote the same word, THEN a person could not tell what has been done from what has not.
    @Test
    void rows_oneOfEachStage_readWaitingTranslatingTranslatedToReview() {
        showRows(mixedChunk());

        assertThat(markerWords()).containsExactly("translated", "translating…", "waiting", "to review");
    }

    // IF a kept segment read "translated", THEN the person would look for a translation that is not there.
    @Test
    void row_keptAsIs_readsKeptAsIs() {
        showRows(List.of(row(1, Stage.KEPT, "Chapter 2")));

        assertThat(markerWords()).containsExactly("kept as is");
    }

    // IF the cell's hover lost the segment, THEN a clipped one-line row could not be read whole.
    @Test
    void rowCell_hovered_givesTheWholeSourceText() {
        showRows(List.of(row(1, Stage.ACCEPTED, DECIDED)));

        assertThat(cells().get(0).getTooltip().getText()).isEqualTo("Source text 1");
    }

    // IF a segment of formatting only showed two empty boxes, THEN the person could not tell what was kept, or that
    // nothing is missing; a kept text with words shows those words on both sides.
    @Test
    void simpleDetail_keptRowWithNoWords_saysSoOnBothSides() {
        showRows(List.of(
                new LiveRow("s-1", "ch7 · p41", "", "", Stage.KEPT, null, List.of(), null),
                new LiveRow("s-2", "ch7 · p42", "IV", null, Stage.KEPT, null, List.of(), null)));

        pick(0);
        assertThat(textOf(DETAIL))
                .containsSubsequence(
                        "No words — only formatting, kept as in the book.",
                        "No words — only formatting, kept as in the book.");
        pick(1);
        assertThat(textOf(DETAIL)).containsSubsequence("IV", "IV").doesNotContain("Not translated yet.");
    }

    // IF Simple left out the source or the target, THEN the person could not compare them.
    @Test
    void simpleDetail_decidedRow_showsItsSourceAndItsTranslation() {
        showRows(mixedChunk());
        pick(0);

        assertThat(textOf(DETAIL)).contains("Source text 1", DECIDED);
        assertThat(isShown("translating-live-call")).isFalse();
    }

    // IF an unfinished row showed an empty translation, THEN it would look lost instead of in the works.
    @Test
    void simpleDetail_rowBeingTranslated_saysItIsBeingTranslatedNow() {
        showRows(mixedChunk());
        pick(1);

        assertThat(textOf(DETAIL)).contains("Source text 2", "Being translated now…");
    }

    @Test
    void simpleDetail_rowNotSentYet_saysNotTranslatedYet() {
        showRows(mixedChunk());
        pick(2);

        assertThat(textOf(DETAIL)).contains("Not translated yet.");
    }

    // IF a flagged row did not say it waits for the person, THEN it would look done.
    @Test
    void simpleDetail_flaggedRow_sendsThePersonToReview() {
        showRows(mixedChunk());
        pick(3);

        assertThat(textOf("translating-live-detail-why")).endsWith(" — open Review to decide");
    }

    // IF the box of what is in flight were missing, THEN the person could not see what the model is working on.
    @Test
    void simpleDetail_callInFlight_listsItsSourcesInTheInFlightBox() {
        showLive(LiveRowFixtures.calls(call42(), mixedChunk()));

        assertThat(textOf("translating-live-inflight"))
                .contains("Being translated now", "Source text 5", "Source text 8");
    }

    // IF Detailed did not name the call, THEN a person chasing a slow call could not tell which one it is.
    @Test
    void detailedLine_callInFlight_namesTheCallItsSegmentsAttemptAndClock() {
        showLive(LiveRowFixtures.calls(call42(), mixedChunk()));

        chooseMode(true);

        assertThat(labelText("translating-live-line"))
                .isEqualTo("Call 42 · Draft · 4 segments · ch2 · p05–p08 · attempt 1 of 2 · 0:21 of 3:00");
    }

    // IF Detailed showed no call for the row, THEN the technical figures could not be read next to the translation.
    @Test
    void detailedDetail_rowWithACall_showsItsPromptAndReply() {
        final CallSnapshot call = LiveCallFixtures.answered(call42(), "{\"items\":[]}");
        final LiveRow inCall =
                new LiveRow("s-5", "ch2 · p05", "Source text 5", "Дощ.", Stage.ACCEPTED, null, List.of(), 42L);
        showLive(LiveRowFixtures.calls(call, List.of(inCall)));

        chooseMode(true);

        assertThat(isShown("translating-live-call")).isTrue();
        assertThat(isShown("translating-live-call-prompt")).isTrue();
        assertThat(isShown("translating-live-call-reply")).isTrue();
    }

    // IF a row never sent said nothing in Detailed, THEN the empty call area would look broken.
    @Test
    void detailedDetail_rowNotSentYet_saysItGoesOutInALaterCall() {
        showLive(LiveRowFixtures.calls(call42(), mixedChunk()));
        chooseMode(true);

        pick(2);

        assertThat(textOf(DETAIL)).contains("Not sent yet — it goes out in a later call.");
        assertThat(isShown("translating-live-call")).isFalse();
    }

    // IF the mode choice did not explain itself, THEN "Simple" and "Detailed" would be guesses.
    @Test
    void modeChoice_hovered_explainsBothViews() {
        showRows(mixedChunk());

        assertThat(TooltipProbe.tipText(required("translating-live-mode-simple")))
                .isEqualTo("Simple: segments and their translation. Detailed: adds the technical figures, what was"
                        + " sent and the reply.");
    }
}
