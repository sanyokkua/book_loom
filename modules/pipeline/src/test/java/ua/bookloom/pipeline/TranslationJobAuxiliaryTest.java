package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.briefWith;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.shutdown;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/** Proves, through a whole job, that the brief's "Also translate" switches decide which auxiliary text is sent. */
class TranslationJobAuxiliaryTest {

    private static final AlsoTranslate ALL_OFF = new AlsoTranslate(false, false, false, false);
    private static final AlsoTranslate METADATA_ONLY = new AlsoTranslate(false, false, true, false);

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // Front matter is off by default, so the title value must neither reach the model nor leave the pending count.
    @Test
    void run_frontMatterByDefault_keepsSourceWithoutCall() {
        final Path source =
                TestBooks.markdown(tempDir.resolve("Book.md"), "---\ntitle: The Lighthouse\n---\n\nHe left.\n");
        final TestProject project = project(source, briefWith(AlsoTranslate.defaults()));
        final ScriptedChatModel model = replies("HE LEFT.");

        final JobReport report = report(job(project, model).run());

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(requestText(model)).doesNotContain("The Lighthouse");
        assertThat(model.requests()).hasSize(1);
        assertThat(recordOfKind(project, SegmentKind.FRONTMATTER_VALUE))
                .extracting(SegmentRecord::status, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.PENDING, null);
        assertThat(countsFor(project, AlsoTranslate.defaults())).isEqualTo(new SegmentCounts(0, 1, 0, 0, 1));
    }

    // Alt text switched off must stay in the source language: it is in no request.
    @Test
    void run_altTextSwitchedOff_keepsSourceWithoutCall() {
        final Path source = TestBooks.epub(
                tempDir.resolve("Book.epub"),
                List.of(List.of("He left. <img src=\"lighthouse.png\" alt=\"A lighthouse at dusk\"/>")),
                "en");
        final TestProject project = project(source, briefWith(ALL_OFF));
        final ScriptedChatModel model = replies("HE LEFT. ⟦g0⟧");

        final JobReport report = report(job(project, model).run());

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(requestText(model)).doesNotContain("A lighthouse at dusk");
        assertThat(model.requests()).hasSize(1);
        assertThat(recordOfKind(project, SegmentKind.ALT).status()).isEqualTo(SegmentStatus.PENDING);
        assertThat(countsFor(project, ALL_OFF).sourceKept()).isGreaterThanOrEqualTo(1);
    }

    // The navigation switch also governs an EPUB's page titles, so 'Chapter One' must not be sent.
    @Test
    void run_navigationOff_keepsPageTitleWithoutCall() {
        final Path source =
                TestBooks.epubTitled(tempDir.resolve("Book.epub"), List.of(List.of("He left.")), "Chapter One");
        final TestProject project = project(source, briefWith(new AlsoTranslate(false, true, false, false)));
        final ScriptedChatModel model = replies("HE LEFT.");

        final JobReport report = report(job(project, model).run());

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(requestText(model)).doesNotContain("Chapter One");
        assertThat(model.requests()).hasSize(1);
        assertThat(recordOfKind(project, SegmentKind.TITLE).status()).isEqualTo(SegmentStatus.PENDING);
    }

    // The switch is judged when the counts are read, so a label accepted earlier counts as kept and is not sent again.
    @Test
    void run_switchTurnedOffAfterRun_keepsAcceptedTitleAsSource() {
        final Path source = TestBooks.epub(tempDir.resolve("Book.epub"), List.of(List.of("One.")), "en");
        final TestProject project = project(source, briefWith(METADATA_ONLY));
        report(job(project, replies("ONE.", "Тест")).run());
        assertThat(stored(project, "aux:title").status()).isEqualTo(SegmentStatus.ACCEPTED);
        switchTo(project, ALL_OFF);
        final ScriptedChatModel second = replies();

        final JobReport report = report(job(project, second).run());

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(second.requests()).isEmpty();
        assertThat(countsFor(project, ALL_OFF))
                .extracting(SegmentCounts::accepted, SegmentCounts::sourceKept, SegmentCounts::pending)
                .containsExactly(1, 2, 0);
        assertThat(stored(project, "aux:title"))
                .extracting(SegmentRecord::status, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.ACCEPTED, "Тест");
    }

    // Progress must not count the title as a section: it is decided last and still reports the last section.
    @Test
    void run_auxiliaryTitle_decidedAtLastSection() {
        final Path source =
                TestBooks.epub(tempDir.resolve("Book.epub"), List.of(List.of("One."), List.of("Two.")), "en");
        final TestProject project = project(source, briefWith(METADATA_ONLY));
        final TranslationJobImpl translation = job(project, replies("ONE.", "TWO.", "Тест"));
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);

        final JobReport report = report(translation.run());

        final List<SegmentDecided> decided = events.stream()
                .filter(SegmentDecided.class::isInstance)
                .map(SegmentDecided.class::cast)
                .toList();
        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(decided).extracting(SegmentDecided::segmentId).last().isEqualTo("aux:title");
        assertThat(decided.getLast().progress())
                .extracting(p -> p.section(), p -> p.sections(), p -> p.pending())
                .containsExactly(2, 2, 0);
    }

    // An after-section pause names body sections only, so the title costs no third pause.
    @Test
    void run_afterSectionWithTitle_pausesTwiceOnly() {
        final Path source =
                TestBooks.epub(tempDir.resolve("Book.epub"), List.of(List.of("One.", "Two."), List.of("Three.")), "en");
        final TestProject project = project(source, briefWith(METADATA_ONLY));
        final TranslationJobImpl translation = job(project, replies("ONE.", "TWO.", "THREE.", "Тест"));
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.AFTER_SECTION));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        final Paused first = awaitPaused(pauses);
        translation.resume();
        final Paused second = awaitPaused(pauses);
        translation.resume();

        assertThat(first.progress())
                .extracting(p -> p.accepted(), p -> p.pending())
                .containsExactly(2, 2);
        assertThat(second.progress())
                .extracting(p -> p.accepted(), p -> p.pending())
                .containsExactly(3, 1);
        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        assertThat(pauses).isEmpty();
        shutdown(workers);
    }

    private static String requestText(final ScriptedChatModel model) {
        return model.requests().stream()
                .map(TranslationJobAuxiliaryTest::textOf)
                .reduce("", String::concat);
    }

    private static String textOf(final ChatRequest request) {
        return request.messages().stream().map(message -> message.content()).reduce("", String::concat);
    }

    private static SegmentCounts countsFor(final TestProject project, final AlsoTranslate switches) {
        return Objects.requireNonNull(
                project.stores()
                        .segments()
                        .countsByStatus(project.id(), switches.keptKinds())
                        .data(),
                "counts");
    }

    private static SegmentRecord stored(final TestProject project, final String segmentId) {
        return TranslationJobTestSupport.stored(project, segmentId);
    }

    private static SegmentRecord recordOfKind(final TestProject project, final SegmentKind kind) {
        return Objects.requireNonNull(
                        project.stores().segments().all(project.id()).data(), "records")
                .stream()
                .filter(record -> record.kind() == kind)
                .findFirst()
                .orElseThrow();
    }

    private static void switchTo(final TestProject project, final AlsoTranslate switches) {
        final Project current = Objects.requireNonNull(
                        project.stores().projects().find(project.id()).data(), "found")
                .orElseThrow();
        project.stores().projects().save(current.withBrief(briefWith(switches)));
    }
}
