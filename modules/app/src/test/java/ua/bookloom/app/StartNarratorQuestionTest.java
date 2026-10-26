package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.OpenedBook;
import ua.bookloom.ui.state.RunState;

/**
 * The Start question about the narrator in the real window, for a book that declares its language (EPUB) and one
 * that does not (Markdown), whose narration can be judged only once the person has chosen the source.
 */
// `slow`: an import and a start in the real window; `test` and the gate run it, `fastTest` leaves it out.
@Tag("slow")
class StartNarratorQuestionTest extends WorkspaceTestBase {

    private static final String[] PARAGRAPHS = {
        "I walked down to the harbour before the sun was up.",
        "My boots were wet and Anna said I did not care at all.",
        "I saw Anna count the boats as they came in.",
        "Anna stood by the rail and she waved at me.",
        "I waved back and I laughed at her wet hat.",
        "My brother was late again, as Anna and I had expected.",
        "I bought bread and I shared it with Anna.",
        "She told me the fish were scarce this year.",
        "I listened to Anna and I watched the gulls circle above.",
        "My day had started well and I was glad of it."
    };

    // IF the narration of a book with no declared language were judged only by the run, THEN Start would never ask who
    // narrates it, and a woman's first-person story would be drafted with the default masculine forms.
    @ParameterizedTest
    @ValueSource(strings = {"Harbour.md", "Harbour.epub"})
    void start_firstPersonBook_asksWhoNarratesAndEnterStartsAnywayAfterTheCharacters(final String file)
            throws Exception {
        final Path book = file.endsWith(".md") ? markdown() : epub();
        startWorkspace(ReviewMode.UNATTENDED);
        onFx(() ->
                injector.getInstance(ua.bookloom.ui.state.ImportViewModel.class).open(book));
        waitUntil(() -> injector.getInstance(CurrentProject.class).book().get() != null);
        chooseLanguages();
        chooseModel();
        seedAnnaWithoutGender();
        onFx(() -> shell.activate(ViewNames.TRANSLATING));

        onFx(() -> ((Button) Objects.requireNonNull(shell.root().lookup("#translating-start"))).fire());
        waitUntil(() -> shell.root().lookup("#narrator-unknown") != null);
        assertThat(runState()).as("nothing starts before the answer").isEqualTo(RunState.IDLE);
        final Node unknown = onFx(() -> shell.root().lookup("#narrator-unknown"));
        assertThat(onFx(() -> ((Button) unknown).isDefaultButton())).isTrue();
        new FxRobot().type(KeyCode.ENTER);

        waitUntil(() -> shell.root().lookup("#genders-card") != null);
        assertThat(onFx(() -> shell.root().lookup("#narrator-card"))).isNull();
        assertThat(runState()).isEqualTo(RunState.IDLE);
    }

    private void chooseLanguages() throws Exception {
        final var brief = injector.getInstance(ua.bookloom.ui.state.BookBriefViewModel.class);
        onFx(() -> brief.setSourceLanguage("en"));
        onFx(() -> brief.setTargetLanguage("uk"));
        waitUntil(() -> !brief.saving().get());
    }

    private void seedAnnaWithoutGender() {
        final OpenedBook open = Objects.requireNonNull(
                onFx(() -> injector.getInstance(CurrentProject.class).book().get()));
        assertThat(injector.getInstance(GlossaryService.class)
                        .add(new GlossaryEntry(
                                "anna", open.projectId(), "Anna", null, TermType.CHARACTER, Gender.UNKNOWN, false))
                        .isOk())
                .isTrue();
    }

    private Path markdown() throws IOException {
        return Files.writeString(
                booksDir.resolve("Harbour.md"),
                "# Morning\n\n" + String.join("\n\n", PARAGRAPHS) + "\n",
                StandardCharsets.UTF_8);
    }

    private Path epub() throws IOException {
        final StringBuilder body = new StringBuilder("<h1>Morning</h1>");
        for (final String paragraph : PARAGRAPHS) {
            body.append("<p>").append(paragraph).append("</p>");
        }
        return TestEpub.write(
                booksDir.resolve("Harbour.epub"),
                "Harbour",
                "    <item id=\"chapter\" href=\"chapter.xhtml\" media-type=\"application/xhtml+xml\"/>\n",
                "<itemref idref=\"chapter\"/>",
                java.util.Map.of("chapter.xhtml", TestEpub.chapter("Morning", body.toString())));
    }
}
