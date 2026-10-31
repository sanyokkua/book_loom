package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * The question about a name whose meaning the book plays on belongs to Prepare (book-brief "Ask about a meaning-bearing
 * name in Prepare, once, and never during a run"): a name a run finds, even one the book explains, is handled by the
 * default and never marked to be asked. The name is invented.
 */
class PrepareTest {

    private static final String BOOK = String.join(
            "\n\n",
            "Мы ждали Светловода у ворот.",
            "Его назвали Светловодом за светлую воду родника.",
            "Вечером пришёл Светловод.");

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // IF the run's own scan marked a name to be asked, THEN a long run would stall at chapter 12 on a question.
    @Test
    void run_midRun_neverAsksAboutAMeaningName() {
        final TestProject project = project(TestBooks.markdown(tempDir.resolve("Book.md"), BOOK), brief("ru", "uk"));

        assertThat(report(job(
                                        project,
                                        replies(
                                                "Ми чекали Світловода біля воріт.",
                                                "Його назвали Світловодом за світлу воду джерела.",
                                                "Увечері прийшов Світловод."))
                                .run())
                        .end())
                .isEqualTo(JobState.COMPLETED);

        final List<GlossaryEntry> glossary = Objects.requireNonNull(
                project.stores().glossary().all(project.id()).data(), "glossary");
        assertThat(glossary).extracting(GlossaryEntry::term).contains("Светловод");
        assertThat(glossary).extracting(GlossaryEntry::meaningChoice).containsOnlyNulls();
    }
}
