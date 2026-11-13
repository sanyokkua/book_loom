package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import javafx.event.Event;
import javafx.scene.control.RadioButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.MeaningChoice;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/**
 * Start translation asks once about the names whose meaning the book plays on (book-brief "Ask about a meaning-bearing
 * name in Prepare, once, and never during a run"): each with its meaning and three choices, sound it out chosen; Save
 * stores the choices and goes on to start, and Back or Escape store the default and start nothing. The names are
 * invented.
 */
class MeaningNameDialogTest extends TranslatingScreenTestBase {

    private static final GlossaryEntry BRIGHTWATER = GlossaryEntry.of(
                    "p1:brightwater", "p1", "Brightwater", null, TermType.PLACE, Gender.UNKNOWN, false)
            .withSuggestedTarget("Брайтвотер")
            .withMeaning("bright water")
            .withMeaningChoice(MeaningChoice.PENDING);
    private static final GlossaryEntry ASHGROVE = GlossaryEntry.of(
                    "p1:ashgrove", "p1", "Ashgrove", null, TermType.CHARACTER, Gender.MALE, false)
            .withSuggestedTarget("Ешґроув")
            .withMeaningChoice(MeaningChoice.PENDING);
    private static final String CARD = "meaning-card";
    private static final int MANY_NAMES = 40;

    private void showNamesStyle(final List<GlossaryEntry> pending) throws Exception {
        projects.on(
                BOOK,
                Result.ok(BookFixtures.withNarratorHint(
                        BookFixtures.frankensteinImport(),
                        new NarratorHint(NarratorHint.Person.THIRD, 0.0, List.of(), 4))));
        openImport();
        openBook(BOOK);
        chooseTarget();
        ThemeTestSupport.onFx(() -> {
            injector.getInstance(ua.bookloom.ui.state.SettingsViewModel.class)
                    .model()
                    .set(MODEL);
            return null;
        });
        glossary.willAnswer(Result.ok(List.of(BRIGHTWATER.withTarget("Брайтвотер"))));
        glossary.willAskMeanings(pending);
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        onFx(() -> button("names-style-start").fire());
    }

    private void awaitAsking() throws Exception {
        awaitFx(() -> isVisibleInScene(CARD));
    }

    private RadioButton choice(final String value, final int row) {
        return (RadioButton) required("meaning-choice-" + value + "-" + row);
    }

    @Test
    void dialog_threeChoices_storeTheAnswer() throws Exception {
        showNamesStyle(List.of(BRIGHTWATER, ASHGROVE));
        awaitAsking();

        final boolean[] defaults = ThemeTestSupport.onFx(() -> new boolean[] {
            choice("transcribe", 0).isSelected(), choice("transcribe", 1).isSelected()
        });
        assertThat(defaults).containsExactly(true, true);
        assertThat(textsUnder(required(CARD)))
                .contains(
                        "Names the book plays on",
                        "Brightwater — “bright water”",
                        "Ashgrove",
                        "Sound it out",
                        "Translate it",
                        "Sound it out, explain once");

        onFx(() -> choice("translate", 0).fire());
        onFx(() -> choice("transcribe_gloss", 1).fire());
        onFx(() -> button("meaning-confirm").fire());

        job.awaitRunStarted();
        assertThat(glossary.meaningAnswers())
                .containsExactly(Map.of(
                        "p1:brightwater", MeaningChoice.TRANSLATE, "p1:ashgrove", MeaningChoice.TRANSCRIBE_GLOSS));
    }

    // Closing the question is an answer too: the names are sounded out and the run does not start.
    @Test
    void dialog_escape_storesTranscribe() throws Exception {
        showNamesStyle(List.of(BRIGHTWATER));
        awaitAsking();

        onFx(() -> Event.fireEvent(
                scene, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));

        awaitFx(() -> !glossary.meaningAnswers().isEmpty());
        assertThat(glossary.meaningAnswers()).containsExactly(Map.of("p1:brightwater", MeaningChoice.TRANSCRIBE));
        assertThat(isVisibleInScene(CARD)).isFalse();
        assertThat(engine.requests()).isEmpty();
    }

    @Test
    void dialog_nothingPending_startsWithoutAsking() throws Exception {
        showNamesStyle(List.of());

        job.awaitRunStarted();
        assertThat(isVisibleInScene(CARD)).isFalse();
        assertThat(glossary.meaningAnswers()).isEmpty();
    }

    // IF a glossary that cannot be read stopped Start, THEN a courtesy question would block the whole run.
    @Test
    void dialog_glossaryUnreadable_startsWithoutAsking() throws Exception {
        glossary.willFailMeanings(AppError.of(ErrorCode.internal, "Glossary", "The glossary could not be read."));

        showNamesStyle(List.of(BRIGHTWATER));

        job.awaitRunStarted();
        assertThat(isVisibleInScene(CARD)).isFalse();
    }

    @Test
    void dialog_inUkrainian_wordsTheQuestion() throws Exception {
        useLocale(java.util.Locale.forLanguageTag("uk"));
        showNamesStyle(List.of(BRIGHTWATER, ASHGROVE));
        awaitAsking();

        assertThat(textsUnder(required(CARD)))
                .contains("Brightwater — «bright water»", "Транскрибувати", "Перекласти", "Зберегти й почати");
    }

    // IF forty names made the card taller than the window, THEN Save would lie below the screen and the question could
    // not be answered.
    @Test
    void dialog_manyNames_scrollsInsideTheWindowAndKeepsSaveReachable() throws Exception {
        final List<GlossaryEntry> many = IntStream.range(0, MANY_NAMES)
                .mapToObj(index -> GlossaryEntry.of(
                                "p1:name" + index, "p1", "Name" + index, null, TermType.PLACE, Gender.UNKNOWN, false)
                        .withMeaning("a long meaning that the book plays on, number " + index)
                        .withMeaningChoice(MeaningChoice.PENDING))
                .toList();
        showNamesStyle(many);
        awaitAsking();

        final double[] edges = ThemeTestSupport.onFx(() -> new double[] {
            scene.getHeight(),
            required("meaning-confirm")
                    .localToScene(required("meaning-confirm").getLayoutBounds())
                    .getMaxY(),
            required(CARD).getLayoutBounds().getHeight()
        });

        assertThat(edges[1]).as("Save inside the window").isLessThanOrEqualTo(edges[0]);
        assertThat(edges[2]).as("card shorter than the window").isLessThan(edges[0]);
    }
}
