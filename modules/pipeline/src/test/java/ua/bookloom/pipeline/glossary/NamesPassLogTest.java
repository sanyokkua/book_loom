package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;

/**
 * The names pass ends with one INFO line of counts for the run log, which the person may share, so it names no name,
 * rendering or book text (glossary "Log the names pass as counts and never as names").
 */
class NamesPassLogTest {

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final Logger counts = (Logger) LoggerFactory.getLogger(NamesPassCounts.class);

    @BeforeEach
    void attach() {
        logged.start();
        counts.addAppender(logged);
    }

    @AfterEach
    void detach() {
        counts.detachAppender(logged);
    }

    private static GlossaryEntry entry(final String term, final TermType type, final List<String> forms) {
        return GlossaryEntry.of("p:" + term, "p", term, "Т" + term, type, Gender.UNKNOWN, false)
                .withForms(forms);
    }

    @Test
    void endOfPass_logsCountsOnly() {
        final List<GlossaryEntry> entries = List.of(
                entry("Ivar", TermType.CHARACTER, List.of("Ivars")).withGenderFrom(Gender.MALE, Origin.LIKELY),
                entry("Велемир", TermType.CHARACTER, List.of("Велемира", "Велемиру"))
                        .withGenderFrom(Gender.MALE, Origin.RULE),
                entry("Mara Quill", TermType.CHARACTER, List.of()).withGenderFrom(Gender.FEMALE, Origin.BOOK),
                entry("Harrowgate", TermType.PLACE, List.of()));

        NamesPassCounts.write(entries, 3);

        final List<ILoggingEvent> info = logged.list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .toList();
        assertThat(info)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("names pass entries=4 forms=3 book=1 rule=1 likely=1 noGender=1 rejected=3");
        assertThat(logged.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(line -> line.contains("Ivar") || line.contains("Велемир") || line.contains("Mara"));
    }
}
