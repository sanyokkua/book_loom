package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TimedJobs.timedJob;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ProviderProbe;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.RecoveryWaiting;

/**
 * A judge call the provider cannot answer waits for the provider like a draft call; flagging the chunk as judged by
 * nobody would hand an overnight outage's every segment to the person. Only a stalled judge (a timeout) degrades, as
 * {@code QualityLoopJudgeUnavailableTest} proves. Every wait is replayed on a scripted clock.
 */
class TranslationJobJudgeOutageTest {

    private static final RunTicks NO_TICKS = (period, tick) -> () -> {};
    private static final String JUDGE_ACCEPT =
            "{\"score\":0.95,\"verdict\":\"accept\",\"findings\":[],\"deferrals\":[]}";

    @TempDir
    private Path tempDir;

    private final ScriptedClock clock = new ScriptedClock();
    private final List<JobEvent> events = new CopyOnWriteArrayList<>();

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // Down for a minute: the wakes at 0:15 and 0:45 find it down, the one at 1:45 finds it back and judges again.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"unreachable", "upstream", "rateLimited"})
    void run_judgeDuringAnOutage_waitsAndJudgesAgainInsteadOfFlagging(final ErrorCode code) {
        final ScriptedChatModel model = replies("ОДИН.", "ДВА.", "ТРИ.")
                .answer(Result.err(AppError.of(code, "Provider failure", "The scripted provider failed.")))
                .answer(Result.ok(new ChatResponse(JUDGE_ACCEPT, FinishReason.STOP)));
        final TranslationJobImpl job = balancedJob(model);
        job.recoverWith(downFor(Duration.ofMinutes(1)));

        final JobReport report = report(job.run());

        assertThat(report)
                .extracting(JobReport::end, JobReport::accepted, JobReport::flagged)
                .containsExactly(JobState.COMPLETED, 3, 0);
        assertThat(events)
                .filteredOn(RecoveryWaiting.class::isInstance)
                .extracting(event -> ((RecoveryWaiting) event).attempt())
                .containsExactly(1, 2, 3);
        assertThat(model.requests())
                .extracting(ChatRequest::callKind)
                .containsExactly(CallKind.DRAFT, CallKind.DRAFT, CallKind.DRAFT, CallKind.JUDGE, CallKind.JUDGE);
    }

    private TranslationJobImpl balancedJob(final ScriptedChatModel model) {
        final RecoveryTimer timer = delay -> {
            clock.advance(delay);
            return 0;
        };
        final Path book = TestBooks.txt(tempDir.resolve("Book.txt"), "One.\n\nTwo.\n\nThree.");
        final TranslationJobImpl job =
                timedJob(project(book, brief("en", "uk", QualityDial.BALANCED)), model, clock, timer, NO_TICKS);
        job.subscribe(events::add);
        return job;
    }

    private ProviderProbe downFor(final Duration outage) {
        return () -> clock.sinceStart().compareTo(outage) >= 0
                ? Result.ok(Duration.ofMillis(40))
                : Result.err(AppError.of(ErrorCode.unreachable, "Down", "The scripted provider is down."));
    }
}
