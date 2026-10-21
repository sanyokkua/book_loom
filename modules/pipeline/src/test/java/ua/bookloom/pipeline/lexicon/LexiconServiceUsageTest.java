package ua.bookloom.pipeline.lexicon;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.project.IdSource;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.CountingIdSource;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.project.ProjectServiceImpl;

/** The recurring terms' "Used" count is read from the book index's postings of the open book. */
class LexiconServiceUsageTest {

    @TempDir
    private Path tempDir;

    private ProjectService projects;
    private LexiconServiceImpl service;

    @BeforeEach
    void setUp() {
        final Injector injector =
                Guice.createInjector(new DocumentModule(), new InMemoryPersistenceModule(), binder -> {
                    binder.bind(Clock.class).toInstance(Clock.systemUTC());
                    binder.bind(IdSource.class).toInstance(new CountingIdSource());
                });
        projects = injector.getInstance(ProjectServiceImpl.class);
        service = injector.getInstance(LexiconServiceImpl.class);
    }

    // The count ignores letter case but not the word: "wardens" is another word.
    @Test
    void usage_oneWordTerm_countsEveryCaseOfTheWordAndNotItsPlural() {
        final String id =
                importOk(paragraphs(8, "The warden waited.", 5, "Warden Holt arrived.", 1, "The wardens left."));
        service.add(id, "warden");

        final Map<String, Integer> usage =
                Objects.requireNonNull(service.usage(id).data());

        assertThat(usage).containsEntry("warden", 13);
    }

    @Test
    void usage_multiWordTerm_countsItsMentions() {
        final String id = importOk(paragraphs(3, "Mira Holt came in.", 2, "Nobody else did."));
        service.add(id, "Mira Holt");

        final Map<String, Integer> usage =
                Objects.requireNonNull(service.usage(id).data());

        assertThat(usage).containsEntry("mira holt", 3);
    }

    @Test
    void usage_termTheBookNeverUses_isZero() {
        final String id = importOk(paragraphs(2, "The rain kept falling."));
        service.add(id, "warden");

        assertThat(Objects.requireNonNull(service.usage(id).data())).containsEntry("warden", 0);
    }

    @Test
    void usage_noBookOpen_answersValidation() {
        final String id = importOk(paragraphs(2, "The rain kept falling."));
        projects.close(id);

        final Result<Map<String, Integer>> usage = service.usage(id);

        assertThat(Objects.requireNonNull(usage.error()).code()).isEqualTo(ErrorCode.validation);
    }

    private String importOk(final String markdown) {
        final Result<ImportedBook> result = projects.create(TestBooks.markdown(tempDir.resolve("Book.md"), markdown));
        assertThat(result.isOk()).as("create %s", result.error()).isTrue();
        return Objects.requireNonNull(Objects.requireNonNull(result.data()).projectId());
    }

    // Pairs of (times, paragraph), each paragraph repeated that many times.
    private static String paragraphs(final Object... timesAndText) {
        return Stream.iterate(0, i -> i < timesAndText.length, i -> i + 2)
                        .flatMap(i ->
                                Collections.nCopies((Integer) timesAndText[i], (String) timesAndText[i + 1]).stream())
                        .reduce((a, b) -> a + "\n\n" + b)
                        .orElse("")
                + "\n";
    }
}
