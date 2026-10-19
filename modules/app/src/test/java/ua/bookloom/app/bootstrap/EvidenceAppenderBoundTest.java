package ua.bookloom.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MarkerFactory;
import ua.bookloom.util.log.EvidenceLog;

/** What the evidence appender holds back is bounded in bytes, not only in lines: a TRACE line may carry a prompt. */
class EvidenceAppenderBoundTest {

    private static final int PROMPT_CHARS = 100_000;
    private static final String PROMPT = "p".repeat(PROMPT_CHARS);

    private final LoggerContext context = new LoggerContext();
    private final Written written = new Written();
    private final EvidenceAppender appender = new EvidenceAppender(written);

    @BeforeEach
    void startAppenders() {
        written.start();
        appender.start();
    }

    /** The file behind the appender, which records what reached it. */
    private static final class Written extends AppenderBase<ILoggingEvent> {

        private final List<String> messages = new CopyOnWriteArrayList<>();

        @Override
        protected void append(final ILoggingEvent event) {
            messages.add(event.getFormattedMessage());
        }
    }

    private void line(@Nullable final String segment, final String message, final boolean keep) {
        final LoggingEvent event = new LoggingEvent(
                EvidenceAppenderBoundTest.class.getName(),
                context.getLogger("ua.bookloom.test"),
                Level.TRACE,
                message,
                null,
                null);
        event.setMDCPropertyMap(segment == null ? Map.of() : Map.of(EvidenceLog.SEGMENT_KEY, segment));
        if (keep) {
            event.addMarker(MarkerFactory.getMarker(EvidenceLog.KEEP_MARKER));
        }
        appender.doAppend(event);
    }

    private void promptsFor(final int segments) {
        IntStream.range(0, segments).forEach(index -> line("seg-" + index, PROMPT, false));
    }

    @Test
    void append_manyPromptSizedLinesNeverKept_holdsNoMoreThanTheByteBudget() {

        promptsFor(60);

        assertThat(appender.heldBytes()).isLessThanOrEqualTo(EvidenceAppender.EVIDENCE_HELD_BYTES);
    }

    @Test
    void append_pastTheByteBudget_forgetsTheOldestSegmentAndKeepsTheNewest() {
        line("seg-first", "first prompt " + PROMPT, false);
        promptsFor(50);

        line("seg-first", "decided", true);
        line("seg-49", "decided", true);

        assertThat(written.messages).noneMatch(message -> message.startsWith("first prompt"));
        assertThat(written.messages)
                .filteredOn(message -> message.length() >= PROMPT_CHARS)
                .hasSize(1);
        assertThat(appender.heldBytes()).isLessThanOrEqualTo(EvidenceAppender.EVIDENCE_HELD_BYTES);
    }

    @Test
    void append_chunkLinesOverTheBudget_dropsTheirOldestLines() {

        IntStream.range(0, 100).forEach(index -> line(null, PROMPT, false));

        assertThat(appender.heldBytes()).isLessThanOrEqualTo(EvidenceAppender.EVIDENCE_HELD_BYTES);
    }

    @Test
    void append_keptSegment_releasesItsBytes() {
        line("seg-a", PROMPT, false);

        line("seg-a", "decided", true);

        assertThat(appender.heldBytes()).isZero();
        assertThat(written.messages).hasSize(2);
    }
}
