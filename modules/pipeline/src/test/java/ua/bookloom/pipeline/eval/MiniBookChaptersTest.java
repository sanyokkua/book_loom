package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.BookInspector;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.project.ChapterBoundaries;

/**
 * The chapter-summary arms run on {@code mini-en}, so its two chapters must stay two under the chapter rule: chapter 1
 * holds only just over the 300-token minimum and would join chapter 2 if it lost a sentence.
 */
class MiniBookChaptersTest {

    @TempDir
    private Path dir;

    @Test
    void chapters_miniEn_twoChapters() {
        final MiniBook book = MiniBook.load(MiniBook.MINI_EN);
        final Injector injector = Guice.createInjector(new DocumentModule());
        final DocumentPort documents = injector.getInstance(DocumentPort.class);
        final Document document =
                Objects.requireNonNull(documents.open(book.writeEpub(dir)).data(), "document");
        final BookProfile profile = Objects.requireNonNull(
                injector.getInstance(BookInspector.class).profile(document).data(), "profile");

        final ChapterBoundaries chapters = ChapterBoundaries.of(document, profile.structure(), book.sourceLanguage());

        assertThat(chapters.chapters()).hasSize(2);
        assertThat(chapters.chapters().getFirst().tokens())
                .isGreaterThanOrEqualTo(ChapterBoundaries.CHAPTER_MIN_TOKENS);
        documents.close(document);
    }
}
