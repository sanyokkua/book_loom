package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.DraftContext;

/** A segment flagged at once names the reason its call failed in its WARN line, never the segment's text. */
class DraftOutcomesLogTest {

    private static final String SOURCE = "Wenna Thrask crossed the salt bridge.";

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

    @Test
    void flaggedAtOnce_repeatLimit_logsReasonToken() {
        final AppError error = AppError.of(
                ErrorCode.upstream,
                "Provider error",
                "The model kept repeating itself.",
                SafeDetails.empty().withReason(FailureReason.REPEAT_LIMIT).render(),
                null);

        DraftOutcomes.flaggedAtOnce(
                DraftAttempt.of(segment(), DraftContext.empty(), ProtectedMask.none(SOURCE)),
                error,
                "model-error",
                "[]");

        assertThat(warnings())
                .filteredOn(line -> line.startsWith("Flagged segment id=ch01.xhtml:3"))
                .singleElement()
                .satisfies(line -> assertThat(line).contains("code=upstream reason=repeat-limit"));
        assertThat(warnings()).noneMatch(line -> line.contains("Thrask"));
    }

    private List<String> warnings() {
        return logged.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static Segment segment() {
        return new Segment(
                "ch01.xhtml:3",
                "ch01.xhtml",
                0,
                SegmentKind.PARAGRAPH,
                SOURCE,
                SOURCE,
                Map.of(),
                "h:" + SOURCE,
                null,
                null,
                new ByteSpanAnchor(0, SOURCE.length()),
                null,
                SegmentStatus.PENDING,
                0.0,
                null,
                List.of(),
                List.of());
    }
}
