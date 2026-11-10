package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;

/**
 * The watchdog ends a call only past one and a half times its own timeout, or after twenty minutes with no decision
 * and no answered proofread call.
 */
class StallWatchdogTest {

    private static final Duration NO_DECISION = Duration.ofMinutes(20);

    private final ScriptedClock clock = new ScriptedClock();
    private final JobControl control = new JobControl(Set.of());
    private final StallWatchdog watchdog = new StallWatchdog(control, clock);

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(StallWatchdog.class);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
        logger.detachAppender(appender);
        appender.stop();
    }

    // A proofread call has a 90 s timeout, so the watchdog ends it at 135 s and not a second before.
    @ParameterizedTest
    @CsvSource({"134, false", "135, false", "136, true"})
    void check_proofreadCallOutstanding_endsItOnlyPastOneHundredThirtyFiveSeconds(
            final long seconds, final boolean ends) {
        inProofreadCall();

        clock.advance(Duration.ofSeconds(seconds));
        watchdog.check();

        assertThat(Thread.interrupted()).isEqualTo(ends);
        assertThat(watchdog.takeStall() != null).isEqualTo(ends);
    }

    // After the stall the run takes the next proofread call as a fresh one with its own full ceiling.
    @Test
    void proofreadCall_outstanding136sOf90s_endsAndRunRecovers() {
        inProofreadCall();
        clock.advance(Duration.ofSeconds(136));
        watchdog.check();
        assertThat(Thread.interrupted()).isTrue();
        assertThat(watchdog.takeStall()).isNotNull();

        control.exitModelCall();
        inProofreadCall();
        clock.advance(Duration.ofSeconds(134));
        watchdog.check();

        assertThat(Thread.interrupted()).isFalse();
        assertThat(watchdog.takeStall()).isNull();
    }

    @Test
    void check_proofreadCallEnded_endsNothing() {
        inProofreadCall();
        watchdog.onEvent(new ModelCallFinished(
                null, CallKind.PROOFREAD, Duration.ofSeconds(3), null, 0, false, List.of("s1"), 1, null));

        clock.advance(Duration.ofMinutes(5));
        watchdog.check();

        assertThat(Thread.interrupted()).isFalse();
    }

    @Test
    void check_noDecisionForTwentyMinutes_endsTheCallInFlight() {
        control.claimRun();
        watchdog.start((period, tick) -> () -> {});
        control.enterModelCall();
        watchdog.onEvent(new ModelCallStarted("s1", CallKind.DRAFT, List.of("s1"), 1, 2, Duration.ofMinutes(30), null));

        clock.advance(Duration.ofMinutes(21));
        watchdog.check();

        assertThat(Thread.interrupted()).isTrue();
        assertThat(watchdog.takeStall())
                .isNotNull()
                .extracting(StallWatchdog.Stall::ceiling)
                .isEqualTo(Duration.ofMinutes(20));
    }

    // IF Proofread's answered windows were not progress, THEN a whole book's Proofread, which decides a segment only
    // when it keeps a rewrite, would be ended after 20 min of healthy calls (the 16.4 whole-book run).
    @Test
    void check_proofreadWindowsAnsweredForTwentySevenMinutesWithNoDecision_endsNothing() {
        startRunning();

        callsEvery(CallKind.PROOFREAD, null, 9, Duration.ofMinutes(3));
        startCall(CallKind.PROOFREAD, Duration.ofSeconds(90));
        clock.advance(Duration.ofSeconds(10));
        watchdog.check();

        assertThat(Thread.interrupted()).isFalse();
        assertThat(watchdog.takeStall()).isNull();
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneSatisfy(line -> assertThat(line).startsWith("No segment decided"));
    }

    // A proofread attempt that failed read no window, so failing attempts are no progress.
    @Test
    void check_proofreadAttemptsFailingForOverTwentyMinutes_endsTheCallInFlight() {
        startRunning();

        callsEvery(CallKind.PROOFREAD, ErrorCode.unreachable, 10, Duration.ofMinutes(2));
        startCall(CallKind.PROOFREAD, Duration.ofSeconds(90));
        clock.advance(Duration.ofSeconds(10));
        watchdog.check();

        assertThat(Thread.interrupted()).isTrue();
        assertThat(watchdog.takeStall())
                .isNotNull()
                .extracting(StallWatchdog.Stall::ceiling)
                .isEqualTo(NO_DECISION);
    }

    // The last answered window restarts the clock, so 21 quiet minutes after it still end the call in flight.
    @Test
    void check_noAnsweredWindowForTwentyOneMinutesAfterTheLast_endsTheCallInFlight() {
        startRunning();
        callsEvery(CallKind.PROOFREAD, null, 3, Duration.ofMinutes(3));

        startCall(CallKind.DRAFT, Duration.ofMinutes(30));
        clock.advance(Duration.ofMinutes(21));
        watchdog.check();

        assertThat(Thread.interrupted()).isTrue();
        assertThat(watchdog.takeStall())
                .isNotNull()
                .extracting(StallWatchdog.Stall::ceiling)
                .isEqualTo(NO_DECISION);
    }

    // A segment repaired again and again is answered every time and never decided: that run is stuck.
    @Test
    void check_repairCallsAnsweredForOverTwentyMinutesWithNoDecision_endsTheCallInFlight() {
        startRunning();

        callsEvery(CallKind.REPAIR, null, 10, Duration.ofMinutes(2));
        startCall(CallKind.REPAIR, Duration.ofMinutes(5));
        clock.advance(Duration.ofSeconds(10));
        watchdog.check();

        assertThat(Thread.interrupted()).isTrue();
        assertThat(watchdog.takeStall())
                .isNotNull()
                .extracting(StallWatchdog.Stall::ceiling)
                .isEqualTo(NO_DECISION);
    }

    // IF a batch call's line named no segment (`DRAFT null`), THEN a person reading the log could not tell which
    // paragraphs the stalled call was about.
    @Test
    void check_batchCallStalls_logNamesEverySegmentOfTheBatch() {
        control.claimRun();
        control.enterModelCall();
        watchdog.onEvent(new ModelCallStarted(
                null, CallKind.DRAFT, List.of("s1", "s2", "s3"), 1, 2, Duration.ofSeconds(60), null));

        clock.advance(Duration.ofSeconds(100));
        watchdog.check();

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(line -> assertThat(line)
                        .contains("DRAFT call segmentIds=[s1, s2, s3]")
                        .doesNotContain("null"));
    }

    // A paused run is waiting on purpose, however long it waits.
    @Test
    void check_runNotRunning_endsNothing() {
        clock.advance(Duration.ofHours(3));

        watchdog.check();

        assertThat(watchdog.takeStall()).isNull();
    }

    private void startRunning() {
        control.claimRun();
        watchdog.start((period, tick) -> () -> {});
    }

    private void startCall(final CallKind kind, final Duration timeout) {
        control.enterModelCall();
        watchdog.onEvent(new ModelCallStarted(null, kind, List.of("s1"), 1, 2, timeout, null));
    }

    // Each call takes `every` and ends with `failure` (null: answered); the watchdog looks after each, as its ticker
    // would.
    private void callsEvery(
            final CallKind kind, final @Nullable ErrorCode failure, final int count, final Duration every) {
        for (int call = 0; call < count; call++) {
            startCall(kind, Duration.ofMinutes(5));
            clock.advance(every);
            watchdog.check();
            control.exitModelCall();
            watchdog.onEvent(new ModelCallFinished(null, kind, every, null, 0, false, List.of("s1"), 1, failure));
        }
    }

    private void inProofreadCall() {
        control.claimRun();
        control.enterModelCall();
        watchdog.onEvent(
                new ModelCallStarted(null, CallKind.PROOFREAD, List.of("s1"), 1, 1, Duration.ofSeconds(90), null));
    }
}
