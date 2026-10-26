package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.state.BookBriefViewModel;

/**
 * The same language on both sides of the Book Brief in the real window over the SQLite file: the brief warns and keeps
 * Continue off, and the pair is never stored, so the project keeps its name and no message says it was renamed.
 */
// `slow`: imports in the real window.
@Tag("slow")
class BriefSamePairStoredTest extends WorkspaceTestBase {

    private static final String TEXT = "It was a dark night.\n\nThe rain fell on the old house.\n";

    @Test
    void setTargetLanguage_sameAsTheSource_isRefusedAndNeitherStoredNorRenamed() throws Exception {
        startStoredWindow();
        openBook(Files.writeString(booksDir.resolve("Night.txt"), TEXT, StandardCharsets.UTF_8), 2);
        final BookBriefViewModel brief = injector.getInstance(BookBriefViewModel.class);
        onFx(() -> brief.setSourceLanguage("en"));
        onFx(() -> brief.setTargetLanguage("uk"));
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> "Night · EN → UK".equals(stored().name()));
        WaitForAsyncUtils.waitForFxEvents();
        final int toastsBefore = toastCount();

        onFx(() -> brief.setTargetLanguage("en"));
        onFx(() -> brief.setGenre("Noir"));
        WaitForAsyncUtils.waitForFxEvents();
        waitUntil(() -> !brief.saving().get());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> brief.sameLanguage().get())).isTrue();
        assertThat(onFx(() -> brief.canContinue().get())).isFalse();
        assertThat(stored().brief().targetLanguage()).isEqualTo("uk");
        assertThat(stored().name()).isEqualTo("Night · EN → UK");
        assertThat(toastCount()).as("no message announces a rename").isEqualTo(toastsBefore);

        onFx(() -> brief.setTargetLanguage("uk"));
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> "Noir".equals(stored().brief().genre()));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(stored().name()).isEqualTo("Night · EN → UK");
        assertThat(toastCount())
                .as("the pair is back as it was stored, so nothing was renamed")
                .isEqualTo(toastsBefore);
    }

    private Project stored() {
        return Objects.requireNonNull(
                        injector.getInstance(ProjectService.class).list().data())
                .getFirst()
                .project();
    }

    private static int toastCount() {
        return onFx(() -> FxToolkit.toolkitContext()
                .getRegisteredStage()
                .getScene()
                .getRoot()
                .lookupAll(".toast")
                .size());
    }
}
