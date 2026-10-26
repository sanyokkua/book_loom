package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ImportState;
import ua.bookloom.ui.state.ImportViewModel;

/**
 * EPUB packages that name one document twice, imported in the real window over the SQLite file: two manifest items
 * under one id are refused as damaged and leave no project behind; a spine that lists one document twice is read once,
 * so its segments are stored under unique ids.
 */
// `slow`: imports in the real window.
@Tag("slow")
class ImportBrokenEpubTest extends WorkspaceTestBase {

    private static final String ONE = TestEpub.chapter("One", "<p>The first chapter starts here.</p>");
    private static final String TWO = TestEpub.chapter("Two", "<p>The second chapter starts here.</p>");

    @Test
    void import_manifestRepeatsAnId_isRefusedAsDamagedAndStoresNothing() throws Exception {
        final Path book = TestEpub.write(
                booksDir.resolve("Twice.epub"),
                "Twice",
                """
                    <item id="c" href="one.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c" href="two.xhtml" media-type="application/xhtml+xml"/>
                """,
                "<itemref idref=\"c\"/><itemref idref=\"c\"/>",
                Map.of("one.xhtml", ONE, "two.xhtml", TWO));
        startStoredWindow();

        onFx(() -> injector.getInstance(ImportViewModel.class).open(book));
        waitUntil(() -> !injector.getInstance(ImportViewModel.class).opening().get());

        final ImportState state = onFx(() -> injector.getInstance(ImportViewModel.class).state().get());
        assertThat(state).isInstanceOf(ImportState.Refused.class);
        assertThat(((ImportState.Refused) state).error().message()).contains("could not be read as a book");
        assertThat(onFx(() -> injector.getInstance(CurrentProject.class).book().get())).isNull();
        assertThat(Objects.requireNonNull(injector.getInstance(ProjectService.class).list().data())).isEmpty();
    }

    @Test
    void import_spineListsADocumentTwice_readsItOnceAndStoresTheBook() throws Exception {
        final Path book = TestEpub.write(
                booksDir.resolve("Again.epub"),
                "Again",
                """
                    <item id="one" href="one.xhtml" media-type="application/xhtml+xml"/>
                    <item id="two" href="two.xhtml" media-type="application/xhtml+xml"/>
                """,
                "<itemref idref=\"one\"/><itemref idref=\"two\"/><itemref idref=\"one\"/>",
                Map.of("one.xhtml", ONE, "two.xhtml", TWO));
        startStoredWindow();

        openBook(book, 2);

        assertThat(Objects.requireNonNull(injector.getInstance(ProjectService.class).list().data())).hasSize(1);
    }
}
