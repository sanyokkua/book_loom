package ua.bookloom.ui.state;

import java.nio.file.Path;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.BookFixtures;

/** Opens a book on the current project without an import, for tests outside this package that need one open. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OpenBookForTest {

    /**
     * Opens the Frankenstein fixture with the given languages; call on the FX thread.
     *
     * @param project the current project to open it on
     * @param source the source language tag, or {@code null}
     * @param target the target language tag, or {@code null}
     */
    public static void open(
            final CurrentProject project, final @Nullable String source, final @Nullable String target) {
        final ImportedBook imported = BookFixtures.frankensteinImport();
        project.open(new OpenedBook(
                "p1",
                Path.of("Frankenstein.epub"),
                imported.inspection(),
                imported.profile(),
                BookBrief.defaults(source).withLanguages(source, target)));
    }
}
