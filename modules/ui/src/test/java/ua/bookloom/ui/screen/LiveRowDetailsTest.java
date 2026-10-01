package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.input.Clipboard;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.SnapshotTmHit;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.LiveRows;
import ua.bookloom.ui.state.RoundTrack;

/**
 * The live row's details: the repair-round tracker, the context the draft was sent with, and the words a pane shows
 * instead of standing empty.
 */
class LiveRowDetailsTest extends TranslatingScreenTestBase {

    private static final String LOCATOR = "ch9 · p02";
    private static final String NO_SUMMARY =
            "No summary yet — on Max quality the model writes one at the end of each chapter.";
    private static final ContextSnapshot CONTEXT = new ContextSnapshot(
            List.of("Коли я приземлився на верхівку ліхтаря.", "Дощ лив стіною."),
            List.of(
                    new SnapshotTerm("Lovelace", "Лавлейс", TermType.CHARACTER, Gender.MALE, true),
                    new SnapshotTerm("Bartimaeus", "Бартімеус", TermType.CHARACTER, Gender.MALE, false),
                    new SnapshotTerm("Pinn", null, TermType.CHARACTER, Gender.MALE, false)),
            List.of(),
            "A djinni is summoned to steal an amulet.",
            "");

    private static LiveRow current(
            final String source, final @Nullable RoundTrack round, final @Nullable ContextSnapshot context) {
        return new LiveRow("s-2", LOCATOR, source, null, null, null, true, false, round, context);
    }

    private void show(final @Nullable LiveRow lastDecided, final @Nullable LiveRow current) throws TimeoutException {
        readyToStart();
        showTranslating();
        mirror().live().publishLiveRows(new LiveRows(lastDecided, current));
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF the round were not shown, THEN a segment stuck in its third repair would look like a slow first draft.
    @Test
    void round_segmentInRepair_showsTheRoundTheScoreAndTheFinding() throws TimeoutException {
        show(null, current("Text.", new RoundTrack(2, 3, 0.85, "meaning"), null));

        assertThat(labelText("live-current-round")).isEqualTo("round 2 of 3 · judge 0.85 · meaning");
    }

    // IF the header did not count what was sent, THEN a person would have to open it to learn it holds nothing.
    @Test
    void context_collapsedHeader_countsWhatWasSent() throws TimeoutException {
        show(null, current("Text.", null, CONTEXT));

        final TitledPane section = (TitledPane) required("live-current-context");
        assertThat(section.isExpanded()).isFalse();
        assertThat(section.getText()).isEqualTo("Context sent to the model · 2 previous · summary · 3 names");
        assertThat(TooltipProbe.tipText(section)).isNotBlank();
    }

    // IF the body were not the context's texts, THEN the person could not check what the model was told: the earlier
    // translations as quote blocks, the summary, and the names beside their renderings with a lock on the locked one.
    @Test
    void context_body_quotesThePrecedingTranslationsAndListsSummaryAndNames() throws TimeoutException {
        show(null, current("Text.", null, CONTEXT));

        final Node body = required("live-current-context-body");
        assertThat(body.lookupAll(".context-quote").stream()
                        .map(node -> ((Label) node).getText())
                        .toList())
                .containsExactly("Коли я приземлився на верхівку ліхтаря.", "Дощ лив стіною.");
        assertThat(textsUnder(body))
                .contains(
                        "Running summary",
                        "A djinni is summoned to steal an amulet.",
                        "Lovelace",
                        "Лавлейс",
                        "Pinn",
                        "(no rendering yet)");
        assertThat(body.lookupAll(".context-lock")).hasSize(1);
    }

    // IF Copy put anything else on the clipboard, THEN a person pasting the context into a note would get a different
    // account of what the model was told.
    @Test
    void context_copy_putsThePlainTextOnTheClipboard() throws TimeoutException {
        show(null, current("Text.", null, CONTEXT));

        onFx(() -> ((Button) required("live-current-context-copy")).fire());

        assertThat(ThemeTestSupport.onFx(() -> Clipboard.getSystemClipboard().getString()))
                .isEqualTo("""
                        Preceding translations
                            “Коли я приземлився на верхівку ліхтаря.”
                            “Дощ лив стіною.”

                        Running summary
                            A djinni is summoned to steal an amulet.

                        Names from the glossary
                            Lovelace → Лавлейс · locked
                            Bartimaeus → Бартімеус
                            Pinn → (no rendering yet)""");
    }

    // IF a row with no context showed the section, THEN it would promise a context that was never announced.
    @Test
    void context_notAnnounced_isNotShown() throws TimeoutException {
        show(null, current("Text.", null, null));

        assertThat(isShown("live-current-context")).isFalse();
    }

    // IF an invisible-only source showed an empty box, THEN the person could not tell a blank segment from a bug.
    @Test
    void source_onlyInvisibleMarks_saysSoInsteadOfAnEmptyBox() throws TimeoutException {
        show(null, current(" ​", null, null));

        assertThat(labelText("live-current-source"))
                .isEqualTo("This segment has no visible text — only spaces or invisible marks.");
    }

    // IF a row with no segment kept its two boxes, THEN the panel would show empty English and Ukrainian panes.
    @Test
    void row_noSegment_isNotShownAtAll() throws TimeoutException {
        show(null, current("Text.", null, null));

        assertThat(isShown("live-last")).isFalse();
        assertThat(isShown("live-current")).isTrue();
    }

    // IF a decided segment with no target showed an empty box, THEN a failed segment would look unfinished.
    @Test
    void target_decidedWithNone_saysThereIsNoTranslation() throws TimeoutException {
        show(new LiveRow("s-1", "ch9 · p01", "Text.", null, null, null, false, false), null);

        assertThat(labelText("live-last-target")).isEqualTo("No translation for this segment.");
    }

    // IF the context body held no texts as words, THEN an empty context would read as a broken panel.
    @Test
    void context_nothingSent_saysSo() throws TimeoutException {
        show(null, current("Text.", null, new ContextSnapshot(List.of(), List.of(), List.of(), null, "")));

        assertThat(textsUnder(required("live-current-context-body")).stream().distinct())
                .containsExactly("Nothing besides the segment and the style sheet.");
        assertThat(((TitledPane) required("live-current-context")).getText())
                .isEqualTo("Context sent to the model · no previous");
    }

    // IF a memory hit were dropped, THEN the person could not see why a repeated passage came back the same.
    @Test
    void context_memoryHit_isListed() throws TimeoutException {
        show(
                null,
                current(
                        "Text.",
                        null,
                        new ContextSnapshot(
                                List.of(),
                                List.of(),
                                List.of(new SnapshotTmHit(SnapshotTmHit.TmHitKind.EXACT, "Yes.", "Так.")),
                                null,
                                "")));

        assertThat(textsUnder(required("live-current-context-body")).stream().distinct())
                .containsExactly("Running summary", NO_SUMMARY, "Translation memory", "Yes. → Так.");
    }

    // IF the summary section were left out while the model has written none, THEN the person could not tell a missing
    // summary from a hidden one — and a list of names was once shown there as if it were the story so far.
    @Test
    void context_noSummaryYet_saysSo() throws TimeoutException {
        show(null, current("Text.", null, new ContextSnapshot(List.of("Так."), List.of(), List.of(), null, "")));

        assertThat(textsUnder(required("live-current-context-body")).stream().distinct())
                .containsExactly("Preceding translations", "Так.", "Running summary", NO_SUMMARY);
    }

    @Test
    void context_modelSummary_isShownUnderItsHeading() throws TimeoutException {
        show(null, current("Text.", null, CONTEXT));

        assertThat(textsUnder(required("live-current-context-body")))
                .containsSubsequence("Running summary", "A djinni is summoned to steal an amulet.")
                .doesNotContain(NO_SUMMARY);
    }
}
