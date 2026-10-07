package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.run.PrepStage;

/** Preparing a run proposes the book's names into an empty glossary, offline, and leaves a filled one alone. */
class PrepStageTest {

    private static final String HALE_BOOK =
            "We met Hale.\n\nThen Hale left.\n\nLater Hale ran.\n\nSo Hale won.\n\nAnd Hale slept.";
    private static final String[] HALE_REPLIES = {
        "Ми зустріли Гейла.", "Потім Гейл пішов.", "Згодом Гейл побіг.", "Тож Гейл переміг.", "І Гейл спав."
    };

    @TempDir
    private Path tempDir;

    @Test
    void run_emptyGlossary_proposesTheScannedNamesBeforeTheFirstDraft() {
        final TestProject project = project(book(), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, replies(HALE_REPLIES));
        final AtomicReference<List<GlossaryEntry>> atFirstCall = new AtomicReference<>();
        translation.subscribe(event -> keepFirstGlossary(project, atFirstCall, event));

        report(translation.run());

        assertThat(atFirstCall.get())
                .singleElement()
                .extracting(
                        GlossaryEntry::term,
                        GlossaryEntry::target,
                        GlossaryEntry::type,
                        GlossaryEntry::gender,
                        GlossaryEntry::locked)
                .containsExactly("Hale", null, TermType.OTHER, Gender.UNKNOWN, false);
    }

    @Test
    void run_glossaryHoldingHale_isNotRescanned() {
        final TestProject project = project(book(), brief("en", "uk"));
        final GlossaryEntry hale = new GlossaryEntry(
                project.id() + ":hale", project.id(), "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, false);
        project.stores().glossary().add(hale);

        report(job(project, replies(HALE_REPLIES)).run());

        assertThat(glossary(project)).containsExactly(hale);
    }

    @Test
    void run_emptyGlossary_sendsNoPrescanRequest() {
        final TestProject project = project(book(), brief("en", "uk"));
        final ScriptedChatModel model = replies(HALE_REPLIES);

        report(job(project, model).run());

        assertThat(formats(model)).doesNotContain("prescan").containsOnly("draft");
    }

    private Path book() {
        return TestBooks.markdown(tempDir.resolve("Book.md"), HALE_BOOK);
    }

    private static List<GlossaryEntry> glossary(final TestProject project) {
        return Objects.requireNonNull(
                project.stores().glossary().all(project.id()).data(), "glossary");
    }

    private static void keepFirstGlossary(
            final TestProject project, final AtomicReference<List<GlossaryEntry>> kept, final JobEvent event) {
        if (event instanceof ModelCallStarted) {
            kept.compareAndSet(null, glossary(project));
        }
    }

    private static final String FIRST_PERSON_BOOK = String.join(
            "\n\n",
            "I walked home through the cold rain. I did not look back.",
            "The street was empty, and I counted the lamps as I passed them.",
            "I had left the letter on the table. I knew that I would regret it.",
            "When I reached the gate, I stopped and listened for a long time.");

    @Test
    void run_firstPersonBook_storesTheNarratorHintAndLeavesTheBriefAlone() {
        final TestProject project =
                project(TestBooks.markdown(tempDir.resolve("First.md"), FIRST_PERSON_BOOK), brief("en", "uk"));

        final PrepStage.Prepared prepared = PrepStage.prepare(
                        project.stores().glossary(),
                        project.stores().lexicon(),
                        project.id(),
                        brief("en", "uk"),
                        Objects.requireNonNull(project.stores().openProjects().get(project.id())))
                .data();

        assertThat(Objects.requireNonNull(prepared).narratorHint())
                .isNotNull()
                .extracting(NarratorHint::person)
                .isEqualTo(NarratorHint.Person.FIRST);
        assertThat(project.stores().projects().find(project.id()).data())
                .hasValueSatisfying(
                        stored -> assertThat(stored.brief().narrator()).isEqualTo(Narrator.unspecified()));
    }

    @Test
    void run_languageWithoutPronounData_storesNoHint() {
        final TestProject project = project(book(), brief("es", "uk"));

        final PrepStage.Prepared prepared = PrepStage.prepare(
                        project.stores().glossary(),
                        project.stores().lexicon(),
                        project.id(),
                        brief("es", "uk"),
                        Objects.requireNonNull(project.stores().openProjects().get(project.id())))
                .data();

        assertThat(Objects.requireNonNull(prepared).narratorHint()).isNull();
    }

    @Test
    void run_emptyGlossaryWithAListedFirstName_proposesItAsACharacterWithASuggestedGender() {
        final String text =
                "We met Hermione.\n\nThen Hermione left.\n\nLater Hermione ran.\n\nSo Hermione won.\n\nAnd Hermione slept.";
        final TestProject project = project(TestBooks.markdown(tempDir.resolve("H.md"), text), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, replies(HALE_REPLIES));

        report(translation.run());

        assertThat(glossary(project))
                .singleElement()
                .extracting(
                        GlossaryEntry::term,
                        GlossaryEntry::type,
                        GlossaryEntry::gender,
                        GlossaryEntry::isGenderSuggested,
                        GlossaryEntry::locked)
                .containsExactly("Hermione", TermType.CHARACTER, Gender.FEMALE, true, false);
    }

    @Test
    void run_heldCharacterWithUnknownGender_getsASuggestedGenderAtPreparation() {
        final TestProject project = project(book(), brief("en", "uk"));
        project.stores()
                .glossary()
                .add(new GlossaryEntry(
                        project.id() + ":harry",
                        project.id(),
                        "Harry",
                        null,
                        TermType.CHARACTER,
                        Gender.UNKNOWN,
                        false));

        report(job(project, replies(HALE_REPLIES)).run());

        assertThat(glossary(project))
                .filteredOn(entry -> entry.term().equals("Harry"))
                .singleElement()
                .extracting(GlossaryEntry::gender, GlossaryEntry::isGenderSuggested)
                .containsExactly(Gender.MALE, true);
    }
}
