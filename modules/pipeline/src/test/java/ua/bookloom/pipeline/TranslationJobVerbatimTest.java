package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.briefWith;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDetail;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.glossary.GlossaryIds;

/**
 * Text with nothing to translate — a chapter number, a scene break, a Roman numeral, one letter, a lone locked name —
 * is kept as it is without a model call, and an auxiliary text repeated across the book is sent once.
 */
class TranslationJobVerbatimTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // The Bartimaeus run sent the chapter number '2' to the model (740 prompt tokens) and then flagged its echo.
    @ParameterizedTest
    @ValueSource(strings = {"2", "***", "XIV", "IV.", "A", "§ 3 —", "1/2"})
    void run_untranslatableParagraph_keptVerbatimWithoutModelCall(final String untranslatable) {
        final Path source = TestBooks.txt(tempDir.resolve("Book.txt"), untranslatable + "\n\nHe left.\n");
        final TestProject project = project(source, brief("en", "uk"));
        final ScriptedChatModel model = replies("Він пішов.");

        final JobReport report = report(job(project, model).run());

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(model.requests()).hasSize(1);
        assertThat(requestText(model)).contains("He left.");
        assertThat(stored(project, "Book.txt:0"))
                .extracting(
                        SegmentRecord::status,
                        SegmentRecord::path,
                        SegmentRecord::machineTarget,
                        SegmentRecord::findings,
                        SegmentRecord::repairRounds)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.VERBATIM, untranslatable, List.of(), 0);
    }

    // A word is text: it still reaches the model, so the rule never keeps a real word untranslated.
    @ParameterizedTest
    @ValueSource(strings = {"Hello", "MIX up", "Chapter 2"})
    void run_wordOrPhrase_isSentToTheModel(final String text) {
        final Path source = TestBooks.txt(tempDir.resolve("Book.txt"), text + "\n");
        final TestProject project = project(source, brief("en", "uk"));
        final ScriptedChatModel model = replies("Переклад");

        report(job(project, model).run());

        assertThat(model.requests()).hasSize(1);
        assertThat(stored(project, "Book.txt:0").path()).isNotEqualTo(SegmentPath.VERBATIM);
    }

    // The events still move the counts: started and decided, with the verbatim count apart from the auto-accepted.
    @Test
    void run_verbatimSegment_isAnnouncedAndCountedApart() {
        final Path source = TestBooks.txt(tempDir.resolve("Book.txt"), "2\n\nHe left.\n\n***\n");
        final TestProject project = project(source, brief("en", "uk"));
        final TranslationJobImpl translation = job(project, replies("Він пішов."));
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);

        report(translation.run());

        final List<SegmentDecided> decided = events.stream()
                .filter(SegmentDecided.class::isInstance)
                .map(SegmentDecided.class::cast)
                .toList();
        assertThat(decided)
                .extracting(event -> Objects.requireNonNull(event.detail()).path())
                .containsExactly(SegmentPath.VERBATIM, SegmentPath.DRAFT, SegmentPath.VERBATIM);
        assertThat(decided.getLast().progress())
                .extracting(
                        JobProgress::accepted,
                        JobProgress::autoAccepted,
                        JobProgress::keptVerbatim,
                        JobProgress::pending)
                .containsExactly(3, 1, 2, 0);
        assertThat(decided.getFirst().detail())
                .extracting(SegmentDetail::findingKinds)
                .isEqualTo(List.of());
    }

    // A segment kept verbatim has no draft event, so a screen can show its target only from the decision itself.
    @Test
    void run_verbatimAndDraftedSegments_decisionsCarryTheirDisplayTarget() {
        final Path source = TestBooks.txt(tempDir.resolve("Book.txt"), "2\n\nHe left.\n\n***\n");
        final TestProject project = project(source, brief("en", "uk"));
        final TranslationJobImpl translation = job(project, replies("Він пішов."));
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);

        report(translation.run());

        assertThat(events)
                .filteredOn(SegmentDecided.class::isInstance)
                .map(SegmentDecided.class::cast)
                .extracting(event -> Objects.requireNonNull(event.detail()).displayTarget())
                .containsExactly("2", "Він пішов.", "***");
    }

    // A segment that is only a locked name takes the name's locked rendering, with no call.
    @Test
    void run_lockedNameAlone_writtenAsItsRenderingWithoutCall() {
        final Path source = TestBooks.txt(tempDir.resolve("Book.txt"), "Bartimaeus\n\nHe left.\n");
        final TestProject project = project(source, brief("en", "uk"));
        project.stores()
                .glossary()
                .add(new GlossaryEntry(
                        GlossaryIds.of(project.id(), "Bartimaeus"),
                        project.id(),
                        "Bartimaeus",
                        "Бартімеус",
                        TermType.CHARACTER,
                        Gender.MALE,
                        true));
        final ScriptedChatModel model = replies("Він пішов.");

        report(job(project, model).run());

        assertThat(model.requests()).hasSize(1);
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.VERBATIM, "Бартімеус");
    }

    // 58 'image' alt texts made 58 calls; identical auxiliary sources now share one.
    @Test
    void run_repeatedAltText_isSentOnceAndAppliedToEverySlot() {
        final Path source = TestBooks.epub(
                tempDir.resolve("Book.epub"),
                List.of(List.of(
                        "He left. <img src=\"a.png\" alt=\"image\"/>", "She came. <img src=\"b.png\" alt=\"image\"/>")),
                "en");
        final TestProject project = project(source, briefWith(new AlsoTranslate(false, true, false, false)));
        final ScriptedChatModel model = replies("Він пішов. ⟦g0⟧", "Вона прийшла. ⟦g0⟧", "зображення");

        final JobReport report = report(job(project, model).run());

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(model.requests()).hasSize(3);
        assertThat(altRecords(project))
                .extracting(SegmentRecord::status, SegmentRecord::machineTarget)
                .containsExactly(
                        tuple(SegmentStatus.ACCEPTED, "зображення"), tuple(SegmentStatus.ACCEPTED, "зображення"));
    }

    private static List<SegmentRecord> altRecords(final TestProject project) {
        return Objects.requireNonNull(
                        project.stores().segments().all(project.id()).data(), "records")
                .stream()
                .filter(record -> record.segmentId().startsWith("aux:alt"))
                .toList();
    }

    private static String requestText(final ScriptedChatModel model) {
        return model.requests().stream().map(TranslationJobVerbatimTest::textOf).reduce("", String::concat);
    }

    private static String textOf(final ChatRequest request) {
        return request.messages().stream().map(message -> message.content()).reduce("", String::concat);
    }
}
