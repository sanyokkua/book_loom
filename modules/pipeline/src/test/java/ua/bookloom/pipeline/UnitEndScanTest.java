package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.glossary.GlossaryIds;

/** The scan at the end of a body unit groups the forms of a new invented name under one base form. */
class UnitEndScanTest {

    private static final String BOOK = String.join(
            "\n\n", "Мы ждали Ростислава у ворот.", "Потом позвали Ростислава снова.", "Вечером пришёл Ростислав.");

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    @Test
    void unitEnd_newInflectedForms_addOneEntryWithForms() {
        final TestProject project = project(TestBooks.markdown(tempDir.resolve("Book.md"), BOOK), brief("ru", "uk"));
        final GlossaryEntry held = GlossaryEntry.of(
                GlossaryIds.of(project.id(), "Тарн"),
                project.id(),
                "Тарн",
                "Тарн",
                TermType.CHARACTER,
                Gender.MALE,
                true);
        Objects.requireNonNull(project.stores().glossary().add(held).data(), "held");

        assertThat(report(job(
                                        project,
                                        replies(
                                                "Ми чекали Ростислава біля воріт.",
                                                "Потім покликали Ростислава знову.",
                                                "Увечері прийшов Ростислав."))
                                .run())
                        .end())
                .isEqualTo(JobState.COMPLETED);

        assertThat(Objects.requireNonNull(
                        project.stores().glossary().all(project.id()).data(), "glossary"))
                .extracting(GlossaryEntry::term, GlossaryEntry::forms)
                .containsExactly(tuple("Тарн", List.of()), tuple("Ростислав", List.of("Ростислава")));
    }

    @Test
    void unitEnd_heldNameInANewForm_listsTheFormOnTheHeldEntry() {
        final TestProject project = project(TestBooks.markdown(tempDir.resolve("Book.md"), BOOK), brief("ru", "uk"));
        final GlossaryEntry held = GlossaryEntry.of(
                GlossaryIds.of(project.id(), "Ростислав"),
                project.id(),
                "Ростислав",
                "Ростислав",
                TermType.CHARACTER,
                Gender.MALE,
                false);
        Objects.requireNonNull(project.stores().glossary().add(held).data(), "held");

        report(job(
                        project,
                        replies(
                                "Ми чекали Ростислава біля воріт.",
                                "Потім покликали Ростислава знову.",
                                "Увечері прийшов Ростислав."))
                .run());

        assertThat(Objects.requireNonNull(
                        project.stores().glossary().all(project.id()).data(), "glossary"))
                .extracting(GlossaryEntry::term, GlossaryEntry::forms)
                .containsExactly(tuple("Ростислав", List.of("Ростислава")));
    }
}
