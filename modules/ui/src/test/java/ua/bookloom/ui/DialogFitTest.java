package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.dialog.ConfirmPrompt;
import ua.bookloom.ui.dialog.ConfirmPrompt.Question;
import ua.bookloom.ui.dialog.ContinueDialog;
import ua.bookloom.ui.dialog.ModelPickDialog;
import ua.bookloom.ui.dialog.RenameDialog;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProjectNameRules;

/**
 * The question cards the screens raise on their own — continue, model pick, rename, About and every confirm — in
 * English and Ukrainian from the smallest window to a full-HD one: no label or button is cut, clipped or squeezed, and
 * the card stays inside the window. The confirm questions share one card, so each is checked at the smallest window
 * and only the longest Ukrainian one at every size.
 */
class DialogFitTest extends ShellTestBase {

    private static final Instant OPENED = Instant.parse("2026-10-21T10:00:00Z");
    private static final List<Question> QUESTIONS = List.of(
            Question.STOP_RUN,
            Question.DISCARD_PROJECT,
            Question.QUIT,
            Question.QUIT_UNEXPORTED,
            Question.CANCEL_EXPORT,
            Question.REPLACE_EXISTING,
            Question.WRITE_NOW,
            Question.WRITE_NOW_EARLY,
            Question.REMOVE_PROJECT,
            Question.DELETE_PROVIDER);

    private static final Object[] TITLE_ARGS = {2};
    private static final Object[] TEXT_ARGS = {74, 142, 4};
    private static final Messages UKRAINIAN = new Messages(() -> Locale.forLanguageTag("uk"));

    // The confirm questions share one card layout, so only the one with the most Ukrainian words — title, text and
    // both buttons — runs at every size; the others run at the smallest window only.
    private static final Question LONGEST_UKRAINIAN = QUESTIONS.stream()
            .max(Comparator.comparingInt(DialogFitTest::ukrainianLength))
            .orElseThrow();

    private static int ukrainianLength(final Question question) {
        return UKRAINIAN.get(question.title(), TITLE_ARGS).length()
                + UKRAINIAN.get(question.text(), TEXT_ARGS).length()
                + UKRAINIAN.get(question.yes()).length()
                + UKRAINIAN.get(question.no()).length();
    }

    private static Named<Consumer<DialogFitTest>> opener(final String name, final Consumer<DialogFitTest> open) {
        return Named.of(name, open);
    }

    private static Stream<Named<Consumer<DialogFitTest>>> cardOpeners() {
        return Stream.concat(continueOpeners(), otherOpeners());
    }

    private static Stream<Named<Consumer<DialogFitTest>>> continueOpeners() {
        return Stream.of(
                opener(
                        "continue, not answering",
                        test -> test.injector
                                .getInstance(ContinueDialog.class)
                                .askNotAnswering(
                                        new ModelSelection("ollama", "gemma4:e4b"), () -> {}, () -> {}, () -> {})),
                opener(
                        "continue, different model",
                        test -> test.injector
                                .getInstance(ContinueDialog.class)
                                .askDifferent(
                                        new ModelSelection("ollama", "gemma4:e4b"),
                                        new ModelSelection("ollama", "qwen3:8b"),
                                        () -> {},
                                        () -> {},
                                        () -> {})));
    }

    private static Stream<Named<Consumer<DialogFitTest>>> otherOpeners() {
        return Stream.of(
                opener(
                        "model pick",
                        test -> test.injector
                                .getInstance(ModelPickDialog.class)
                                .ask(
                                        "ollama",
                                        List.of("gemma4:e4b", "qwen3:8b", "llama3.1:8b"),
                                        model -> {},
                                        () -> {})),
                opener("rename", DialogFitTest::askRename),
                opener("about", test -> ((javafx.scene.control.Button) test.required("shell-about")).fire()));
    }

    private static Named<Consumer<DialogFitTest>> confirmOpener(final Question question) {
        return opener(
                "confirm " + question.title() + (question.equals(LONGEST_UKRAINIAN) ? " (longest in Ukrainian)" : ""),
                test -> test.injector
                        .getInstance(ConfirmPrompt.class)
                        .ask(question, List.of(TITLE_ARGS), List.of(TEXT_ARGS), () -> {}));
    }

    private static void askRename(final DialogFitTest test) {
        final Project project = ProjectFixtures.project(
                "p-uk",
                ProjectFixtures.BOOK_FILE,
                ProjectFixtures.BOOK_HASH,
                "en",
                "uk",
                NameOrigin.USER,
                "Earth Gravity · my run",
                OPENED);
        test.injector
                .getInstance(RenameDialog.class)
                .ask(
                        ProjectFixtures.row(project, 0, null),
                        new ProjectNameRules(Set.of(), "Earth Gravity · EN → UK", true),
                        null,
                        null,
                        name -> {},
                        () -> {},
                        () -> {});
    }

    static Stream<Arguments> dialogsInEachLanguageAndSize() {
        return FitMatrix.cases(cardOpeners(), FitMatrix.FAST_SIZES);
    }

    static Stream<Arguments> everyQuestionAtTheSmallestWindow() {
        return FitMatrix.cases(
                QUESTIONS.stream().map(DialogFitTest::confirmOpener), FitMatrix.FAST_SIZES.subList(0, 1));
    }

    static Stream<Arguments> longestQuestionAtTheOtherSizes() {
        return FitMatrix.cases(
                confirmOpener(LONGEST_UKRAINIAN), "uk", FitMatrix.FAST_SIZES.subList(1, FitMatrix.FAST_SIZES.size()));
    }

    static Stream<Arguments> dialogsAtTheWidthsBetween() {
        return Stream.concat(
                FitMatrix.cases(cardOpeners(), FitMatrix.SLOW_SIZES),
                FitMatrix.cases(confirmOpener(LONGEST_UKRAINIAN), "uk", FitMatrix.SLOW_SIZES));
    }

    // IF a Ukrainian button or sentence were cut, or the card ran past the window, THEN the answer could not be read
    // or given.
    @ParameterizedTest(name = "{0} in {1} at {2}")
    @MethodSource({"dialogsInEachLanguageAndSize", "everyQuestionAtTheSmallestWindow", "longestQuestionAtTheOtherSizes"
    })
    void dialog_inEachLanguageAndSize_cutsNothingAndStaysInsideTheWindow(
            final Consumer<DialogFitTest> open, final String language, final double[] size) {
        assertFits(open, language, size);
    }

    // IF a card's layout switched between the widths the inner loop checks, THEN a button could be cut there.
    @Tag("slow")
    @ParameterizedTest(name = "{0} in {1} at {2}")
    @MethodSource("dialogsAtTheWidthsBetween")
    void dialog_atTheWidthsBetween_cutsNothingAndStaysInsideTheWindow(
            final Consumer<DialogFitTest> open, final String language, final double[] size) {
        assertFits(open, language, size);
    }

    private void assertFits(final Consumer<DialogFitTest> open, final String language, final double[] size) {
        useLocale(Locale.forLanguageTag(language));
        resizeScene(size[0], size[1]);
        onFx(() -> open.accept(this));

        final List<String> halfHidden = ThemeTestSupport.onFx(() -> FitProbe.halfHidden(scene.getRoot()));

        assertThat(scene.getRoot().lookupAll(".dialog-card"))
                .as("the dialog is open")
                .isNotEmpty();
        FitMatrix.assertFits(scene, size);
        assertThat(halfHidden).as("buttons a scroll edge cuts in half").isEmpty();
    }
}
