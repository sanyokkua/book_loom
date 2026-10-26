package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.UUID;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.NamesStyleViewModel;

/**
 * Clearing the target of a locked glossary row in the real window over the SQLite file: the store refuses it, the
 * screen says why, and the cell shows the stored target again at once instead of the empty text that was refused.
 */
// `slow`: imports in the real window.
@Tag("slow")
class GlossaryLockedTargetStoredTest extends WorkspaceTestBase {

    private static final String TEXT = "Mira walked to the harbour.\n\nMira saw the ships.\n";

    @Test
    void setTarget_blankOnALockedRow_isRefusedAndTheCellShowsTheStoredTargetAgain() throws Exception {
        startStoredWindow();
        openBook(Files.writeString(booksDir.resolve("Harbour.txt"), TEXT, StandardCharsets.UTF_8), 2);
        final String project = Objects.requireNonNull(onFx(
                        () -> injector.getInstance(CurrentProject.class).book().get()))
                .projectId();
        final GlossaryEntry locked = new GlossaryEntry(
                UUID.randomUUID().toString(), project, "Mira", "Міра", TermType.CHARACTER, Gender.FEMALE, true);
        assertThat(injector.getInstance(GlossaryService.class).add(locked).isOk())
                .isTrue();
        final BookBriefViewModel brief = injector.getInstance(BookBriefViewModel.class);
        onFx(() -> brief.setSourceLanguage("en"));
        onFx(() -> brief.setTargetLanguage("uk"));
        waitUntil(() -> !brief.saving().get());
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        waitUntil(() -> "Міра".equals(textOf(targetField())));

        onFx(() -> {
            final TextField field = Objects.requireNonNull(targetField());
            field.requestFocus();
            field.setText("");
        });
        WaitForAsyncUtils.waitForFxEvents();
        onFx(() -> {
            assertThat(Objects.requireNonNull(targetField()).isFocused()).isTrue();
            Objects.requireNonNull(shell.root().lookup("#names-style-search")).requestFocus();
        });
        final NamesStyleViewModel names = injector.getInstance(NamesStyleViewModel.class);
        waitUntil(() -> names.restorations().get() > 0);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> textOf(targetField()))).isEqualTo("Міра");
    }

    private static @Nullable String textOf(final @Nullable TextField field) {
        return field == null ? null : field.getText();
    }

    @SuppressWarnings("unchecked")
    private @Nullable TextField targetField() {
        final TableView<GlossaryEntry> table =
                (TableView<GlossaryEntry>) shell.root().lookup("#names-style-table");
        return table == null ? null : (TextField) table.lookup(".table-row-cell .text-field");
    }
}
