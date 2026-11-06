package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.SummaryBooks.CHAPTER_SEGMENTS;
import static ua.bookloom.pipeline.SummaryBooks.book;
import static ua.bookloom.pipeline.SummaryBooks.model;
import static ua.bookloom.pipeline.SummaryBooks.stored;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.pipeline.ChapterSummaryKept;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.project.ChapterSummary;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A pause aborts a chapter-summary call without waiting for the provider and the call is made again on resume; a stop
 * keeps nothing ({@code specs/resume/spec.md} "Pause on request at the next boundary", {@code
 * specs/translation-pipeline/spec.md} "Pause and stop act without waiting for the provider").
 */
class PauseTest {

    // Chapter 1's four drafts come first, then its summary call.
    private static final int SUMMARY_REQUEST = CHAPTER_SEGMENTS + 1;
    private static final String NOT_KEPT = "Chapter summary not kept";

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger bookloom;
    private @Nullable Level previousLevel;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void attachAppender() {
        bookloom = (Logger) LoggerFactory.getLogger("ua.bookloom");
        previousLevel = bookloom.getLevel();
        bookloom.setLevel(Level.DEBUG);
        appender.start();
        bookloom.addAppender(appender);
    }

    @AfterEach
    void cleanUp() {
        bookloom.detachAppender(appender);
        bookloom.setLevel(previousLevel);
        appender.stop();
        TranslationJobTestSupport.shutdownAll();
    }

    /** A run of a two-chapter book whose chapter-1 summary call is held until the job interrupts it. */
    private record Held(
            TestProject project,
            ScriptedChatModel model,
            TranslationJobImpl job,
            List<JobEvent> events,
            LinkedBlockingQueue<Paused> pauses,
            Future<Result<JobReport>> run) {}

    private Held heldAtSummary() {
        final TestProject project = book(tempDir, 2, true);
        final ScriptedChatModel model = model().blockNthRequest(SUMMARY_REQUEST);
        final TranslationJobImpl translation = job(project, model);
        final List<JobEvent> events = new CopyOnWriteArrayList<>();
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> {
            events.add(event);
            capturePaused(pauses, event);
        });
        final Future<Result<JobReport>> run = executor().submit(translation::run);
        model.awaitRequests(SUMMARY_REQUEST);
        return new Held(project, model, translation, events, pauses, run);
    }

    private List<String> warnings() {
        return appender.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static List<ChatRequest> summaryRequests(final ScriptedChatModel model) {
        return model.requests().stream().filter(SummaryBooks::isSummary).toList();
    }

    @Test
    void pause_duringSummaryCall_resendsOnResume() {
        final Held held = heldAtSummary();

        held.job().pause();
        final Paused pause = awaitPaused(held.pauses());
        final List<JobEvent> atPause = List.copyOf(held.events());
        final List<String> warnedAtPause = warnings();
        held.job().resume();
        final JobReport report = report(await(held.run()));

        assertThat(SummaryBooks.isSummary(held.model().requests().get(SUMMARY_REQUEST - 1)))
                .as("the held request is the summary call")
                .isTrue();
        assertThat(pause.reason()).isEqualTo(PauseReason.REQUESTED);
        assertThat(atPause).noneMatch(ChapterSummaryKept.class::isInstance);
        assertThat(warnedAtPause).noneMatch(line -> line.startsWith(NOT_KEPT));
        final List<ChatRequest> sent = summaryRequests(held.model());
        assertThat(sent).hasSize(2);
        assertThat(sent.get(1).messages()).isEqualTo(sent.get(0).messages());
        assertThat(stored(held.project()))
                .extracting(ChapterSummary::chapterKey)
                .containsExactly("Book.md:0");
        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(warnings()).noneMatch(line -> line.startsWith(NOT_KEPT));
    }

    @Test
    void stop_duringSummaryCall_keepsNothing() {
        final Held held = heldAtSummary();

        held.job().cancel();
        final JobReport report = report(await(held.run()));

        assertThat(report.end()).isEqualTo(JobState.CANCELLED);
        assertThat(stored(held.project())).isEmpty();
        assertThat(held.events()).noneMatch(ChapterSummaryKept.class::isInstance);
        assertThat(summaryRequests(held.model())).hasSize(1);
    }
}
