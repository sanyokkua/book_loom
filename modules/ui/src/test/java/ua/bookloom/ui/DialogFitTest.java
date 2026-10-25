package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.dialog.ConfirmDialog;
import ua.bookloom.ui.dialog.ConfirmDialog.Question;
import ua.bookloom.ui.dialog.ContinueDialog;
import ua.bookloom.ui.dialog.ModelPickDialog;
import ua.bookloom.ui.dialog.RenameDialog;
import ua.bookloom.ui.state.ProjectNameRules;

/**
 * The question cards the screens raise on their own — continue, model pick, rename and every confirm — in English and
 * Ukrainian from the smallest window to a full-HD one: no label or button is cut, clipped or squeezed, and the card
 * stays inside the window.
 */
class DialogFitTest extends ShellTestBase {

    private static final Instant OPENED = Instant.parse("2026-10-21T10:00:00Z");
    private static final List<double[]> SIZES = List.of(
            new double[] {CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT},
            new double[] {1000, CONTENT_AT_MINIMUM_HEIGHT},
            new double[] {1040, CONTENT_AT_MINIMUM_HEIGHT},
            new double[] {1100, CONTENT_AT_MINIMUM_HEIGHT},
            new double[] {1250, CONTENT_AT_MINIMUM_HEIGHT},
            new double[] {1440, CONTENT_AT_MINIMUM_HEIGHT},
            new double[] {1700, CONTENT_AT_MINIMUM_HEIGHT},
            new double[] {1920, 700});
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

    private static Named<Consumer<DialogFitTest>> opener(final String name, final Consumer<DialogFitTest> open) {
        return Named.of(name, open);
    }

    private static Stream<Named<Consumer<DialogFitTest>>> openers() {
        return Stream.concat(Stream.concat(continueOpeners(), otherOpeners()), confirmOpeners());
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

    private static Stream<Named<Consumer<DialogFitTest>>> confirmOpeners() {
        return QUESTIONS.stream()
                .map(question -> opener(
                        "confirm " + question.title(),
                        test -> test.injector
                                .getInstance(ConfirmDialog.class)
                                .ask(question, List.of(2), List.of(74, 142, 4), () -> {})));
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
        return openers()
                .flatMap(open -> Stream.of("en", "uk")
                        .flatMap(language -> SIZES.stream()
                                .map(size -> Arguments.of(
                                        open, language, Named.of((int) size[0] + " x " + (int) size[1], size)))));
    }

    // IF a Ukrainian button or sentence were cut, or the card ran past the window, THEN the answer could not be read
    // or given.
    @ParameterizedTest(name = "{0} in {1} at {2}")
    @MethodSource("dialogsInEachLanguageAndSize")
    void dialog_inEachLanguageAndSize_cutsNothingAndStaysInsideTheWindow(
            final Consumer<DialogFitTest> open, final String language, final double[] size) {
        useLocale(Locale.forLanguageTag(language));
        resizeScene(size[0], size[1]);
        onFx(() -> open.accept(this));

        final List<String> cut = ThemeTestSupport.onFx(() -> FitProbe.cutParts(scene.getRoot()));
        final List<String> outside =
                ThemeTestSupport.onFx(() -> FitProbe.cardsOutside(scene.getRoot(), size[0], size[1]));

        final List<String> overflowing = ThemeTestSupport.onFx(() -> FitProbe.overflowing(scene.getRoot(), size[0]));
        final List<String> squeezed = ThemeTestSupport.onFx(() -> FitProbe.squeezed(scene.getRoot()));
        final List<String> halfHidden = ThemeTestSupport.onFx(() -> FitProbe.halfHidden(scene.getRoot()));

        assertThat(scene.getRoot().lookupAll(".dialog-card"))
                .as("the dialog is open")
                .isNotEmpty();
        assertThat(overflowing).as("controls past their viewport or card").isEmpty();
        assertThat(squeezed).as("controls drawn narrower than they ask").isEmpty();
        assertThat(cut).as("parts drawn shorter than their words").isEmpty();
        assertThat(halfHidden).as("buttons a scroll edge cuts in half").isEmpty();
        assertThat(outside).as("dialog cards past the window's edge").isEmpty();
    }
}
