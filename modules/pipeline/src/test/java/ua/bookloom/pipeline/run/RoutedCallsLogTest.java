package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobStage;

/**
 * A step flagged after its pauses names why its call failed in its WARN line, read from the error's typed reason, so a
 * night's run log says at a glance what made the run give the step up.
 */
class RoutedCallsLogTest {

    private static final JobProgress PROGRESS = new JobProgress(JobStage.TRANSLATE, 1, 1, 0, 0, 3);
    private static final String PROVIDER_SENTENCE = "PROVIDER-SENTENCE-MARK";

    private final RoutedCalls calls = new RoutedCalls(new RoutedCallsTest.ResumingBoundaries());
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void attach() {
        final Logger bookloom = (Logger) LoggerFactory.getLogger("ua.bookloom");
        bookloom.setLevel(Level.DEBUG);
        logged.start();
        bookloom.addAppender(logged);
    }

    @AfterEach
    void detach() {
        ((Logger) LoggerFactory.getLogger("ua.bookloom")).detachAppender(logged);
    }

    // A model that does not fit in memory waits for the person; past the step's pauses the step is flagged. A timeout
    // carries no reason, so its line says none rather than leaving the field out.
    @ParameterizedTest
    @CsvSource({"modelUnavailable, OUT_OF_MEMORY, reason=out-of-memory", "timeout, , reason=none"})
    void flaggedStep_logsReasonToken(
            final ErrorCode code, @Nullable final FailureReason reason, final String expected) {
        final Step<String> step = calls.untilAnsweredOrFlagged(
                () -> PROGRESS,
                "Book.txt:4",
                new RoutedCalls.StepName("draft", "Book.txt:4"),
                failing(code, reason),
                error -> "flagged");

        assertThat(step).isEqualTo(new Step.Done<>("flagged"));
        assertThat(warnings())
                .filteredOn(line -> line.startsWith("Flagging step=draft:Book.txt:4"))
                .singleElement()
                .satisfies(line -> assertThat(line).contains("code=" + code).contains(expected));
        assertThat(warnings()).noneMatch(line -> line.contains(PROVIDER_SENTENCE));
    }

    // Fails every time, with the reason in the typed details and the provider's own words only in the message.
    private static Supplier<Result<String>> failing(final ErrorCode code, @Nullable final FailureReason reason) {
        final SafeDetails details =
                reason == null ? SafeDetails.empty() : SafeDetails.empty().withReason(reason);
        return () -> Result.err(AppError.of(
                code, "Scripted failure", "The provider said " + PROVIDER_SENTENCE, details.render(), null));
    }

    private List<String> warnings() {
        return logged.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
