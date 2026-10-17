package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.ChunkRunFixtures.shown;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.glossary.GlossaryIds;
import ua.bookloom.pipeline.glossary.GlossaryServiceImpl;

/**
 * A brand or a title kept in Latin letters ships translated (15i.T4): the glossary action that sets the entry's target
 * locks it, so the run hides it behind a token, the model never sees the Latin words, and the Latin-run gate has
 * nothing to block. The model is scripted at the call seam, once for the glossary action and once for the run; the
 * text is invented.
 */
class TranslationJobBrandTest {

    private static final String BRAND = "KESSEL OPTIK";
    private static final String TITLE = "Weekly Courier Digest";
    private static final String UUID_TEXT = "3f2b8c1e-5d4a-4b7e-9a10-6c2d8e4f1a77";

    @TempDir
    private Path tempDir;

    private final Injector injector = Guice.createInjector(
            new DocumentModule(),
            new PersistenceModule(),
            binder -> binder.bind(Clock.class).toInstance(Clock.systemUTC()));

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // Under Keep original (15j.J1: a brand follows the name policy). IF the brand stayed an unlocked entry with no
    // target, THEN the draft would carry the Latin words, the Latin-run
    // gate would block the paragraph and the run would spend its repairs on a word that is right as it is.
    @Test
    void run_brandReadByTheReview_shipsTranslatedAroundTheMaskedBrand() {
        final TestProject project = openProject(
                List.of(
                        "Nell bought a lens from KESSEL OPTIK on her way home.",
                        "The window of KESSEL OPTIK glowed in the rain.",
                        "Later a letter came from KESSEL OPTIK."),
                NamePolicy.KEEP_ORIGINAL);
        add(project, "Nell", "Нелл", TermType.CHARACTER, Gender.FEMALE);
        add(project, BRAND, null, TermType.OTHER, Gender.UNKNOWN);

        glossaryReview(project, QualityDial.BALANCED, brandReading());
        final ScriptedChatModel model = replies(
                "Нелл купила об’єктив у ⟦g0⟧ дорогою додому.",
                "Вітрина ⟦g0⟧ світилася під дощем.",
                "Пізніше надійшов лист від ⟦g0⟧.");
        final JobReport report = report(job(project, model).run());

        assertThat(brandEntry(project))
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(BRAND, true);
        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(userMessage(model.requests().getFirst()))
                .contains(shown("Nell bought a lens from ⟦g0⟧ on her way home."))
                .doesNotContain("from KESSEL OPTIK");
        assertThat(IntStream.range(0, 3).mapToObj(index -> stored(project, "Book.txt:" + index)))
                .extracting(SegmentRecord::status, SegmentRecord::machineTarget)
                .containsExactly(
                        tuple(SegmentStatus.ACCEPTED, "Нелл купила об’єктив у KESSEL OPTIK дорогою додому."),
                        tuple(SegmentStatus.ACCEPTED, "Вітрина KESSEL OPTIK світилася під дощем."),
                        tuple(SegmentStatus.ACCEPTED, "Пізніше надійшов лист від KESSEL OPTIK."));
    }

    // The same outcome for a title the name policy keeps in Latin letters, with no reading at all.
    @Test
    void run_titleKeptInLatinByThePolicy_shipsTranslatedAroundTheMaskedTitle() {
        final TestProject project = openProject(
                List.of("On the train he read the latest Weekly Courier Digest from cover to cover."),
                NamePolicy.KEEP_ORIGINAL);
        add(project, TITLE, null, TermType.TITLE, Gender.UNKNOWN);

        glossaryReview(project, QualityDial.FAST, nameVerdict(TITLE));
        final ScriptedChatModel model = replies("У поїзді він прочитав свіжий ⟦g0⟧ від початку до кінця.");
        report(job(project, model).run());

        assertThat(glossaryEntry(project, TITLE))
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(TITLE, true);
        assertThat(userMessage(model.requests().getFirst()))
                .contains(shown("On the train he read the latest ⟦g0⟧ from cover to cover."));
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::machineTarget)
                .containsExactly(
                        SegmentStatus.ACCEPTED,
                        "У поїзді він прочитав свіжий Weekly Courier Digest від початку до кінця.");
    }

    // An identifier is no phrase: the run keeps it letter for letter and nothing blocks the paragraph (holds before
    // 15i.T4 too; the identifier check masks it ahead of the Latin-run check).
    @Test
    void run_paragraphWithAnIdentifier_shipsWithTheIdentifierKeptLetterForLetter() {
        final TestProject project = openProject(
                List.of("The ticket was filed under " + UUID_TEXT + " and nobody read it."), NamePolicy.TRANSLITERATE);

        final ScriptedChatModel model = replies("Заявку подали під номером " + UUID_TEXT + ", і ніхто її не прочитав.");
        report(job(project, model).run());

        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::machineTarget)
                .containsExactly(
                        SegmentStatus.ACCEPTED, "Заявку подали під номером " + UUID_TEXT + ", і ніхто її не прочитав.");
    }

    private TestProject openProject(final List<String> paragraphs, final NamePolicy policy) {
        final Path book = TestBooks.txt(tempDir.resolve("Book.txt"), String.join("\n\n", paragraphs) + "\n");
        return project(injector, UnaryOperator.<DocumentPort>identity(), book, withNames(brief("en", "uk"), policy));
    }

    /** Runs the glossary's «Review with model» at the given dial, then puts the project back on the Fast dial. */
    private void glossaryReview(final TestProject project, final QualityDial dial, final ScriptedChatModel model) {
        final ProjectRepository projects = project.stores().projects();
        final Project held =
                Objects.requireNonNull(projects.find(project.id()).data()).orElseThrow();
        projects.save(held.withBrief(withDial(held.brief(), dial)));

        final var reviewed = injector.getInstance(GlossaryServiceImpl.class).review(project.id(), model, event -> {});

        assertThat(reviewed.isOk())
                .as("the glossary review ran: %s", reviewed.error())
                .isTrue();
        projects.save(held);
    }

    private static ScriptedChatModel brandReading() {
        return new ScriptedChatModel()
                .answerTo(
                        "term-reading",
                        Result.ok(new ChatResponse(
                                "{\"windows\":[" + windowsOf("organisation") + "],\"speaking\":false,\"brand\":true}",
                                FinishReason.STOP)))
                .answer(Result.ok(new ChatResponse("{\"suggestions\":[]}", FinishReason.STOP)));
    }

    private static ScriptedChatModel nameVerdict(final String term) {
        return new ScriptedChatModel()
                .answerTo(
                        "review-terms",
                        Result.ok(new ChatResponse(
                                "{\"verdicts\":[{\"term\":\"" + term
                                        + "\",\"verdict\":\"name\",\"type\":\"other\",\"gender\":\"unknown\","
                                        + "\"windows\":[]}]}",
                                FinishReason.STOP)))
                .answer(Result.ok(new ChatResponse("{\"suggestions\":[]}", FinishReason.STOP)));
    }

    private static String windowsOf(final String kind) {
        return IntStream.rangeClosed(1, 12)
                .mapToObj(number ->
                        "{\"window\":%d,\"quote\":\"\",\"usage\":\"name\",\"kind\":\"%s\",\"gender\":\"none\"}"
                                .formatted(number, kind))
                .collect(Collectors.joining(","));
    }

    private static void add(
            final TestProject project,
            final String term,
            @Nullable final String target,
            final TermType type,
            final Gender gender) {
        project.stores()
                .glossary()
                .add(new GlossaryEntry(
                        GlossaryIds.of(project.id(), term), project.id(), term, target, type, gender, false));
    }

    private static GlossaryEntry brandEntry(final TestProject project) {
        return glossaryEntry(project, BRAND);
    }

    private static GlossaryEntry glossaryEntry(final TestProject project, final String term) {
        return Objects.requireNonNull(
                        project.stores().glossary().all(project.id()).data())
                .stream()
                .filter(entry -> entry.term().equals(term))
                .findFirst()
                .orElseThrow();
    }

    private static BookBrief withNames(final BookBrief brief, final NamePolicy policy) {
        return new BookBrief(
                brief.sourceLanguage(),
                brief.targetLanguage(),
                brief.genre(),
                brief.register(),
                brief.voiceEra(),
                brief.audience(),
                policy,
                brief.foreignPassages(),
                brief.footnotes(),
                brief.units(),
                brief.balance(),
                brief.alsoTranslate(),
                brief.dial());
    }

    private static BookBrief withDial(final BookBrief brief, final QualityDial dial) {
        return new BookBrief(
                brief.sourceLanguage(),
                brief.targetLanguage(),
                brief.genre(),
                brief.register(),
                brief.voiceEra(),
                brief.audience(),
                brief.names(),
                brief.foreignPassages(),
                brief.footnotes(),
                brief.units(),
                brief.balance(),
                brief.alsoTranslate(),
                dial);
    }
}
