package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ua.bookloom.pipeline.prompt.BatchNotesSwitch;

/**
 * The notes arm (15i.X2): a model asked for a one-line note before each target writes it; the note is stripped and
 * logged at TRACE, never read as part of the translation, and a reply without notes is as good as one with them.
 */
class BatchNotesParserTest {

    private static final BatchReplyParser PARSER = new BatchReplyParser(new ObjectMapper());
    private static final List<BatchItem> ITEMS = List.of(
            new BatchItem("1", "She opened the door and looked outside."),
            new BatchItem("2", "He said nothing for a long while."),
            new BatchItem("3", "1881"),
            new BatchItem("4", "⟦g0⟧Hello⟦g1⟧ there, my old friend."));
    private static final String T1 = "Вона відчинила двері й визирнула надвір.";
    private static final String T2 = "Він довго нічого не казав.";
    private static final String T3 = "1881";
    private static final String T4 = "⟦g0⟧Привіт⟦g1⟧, мій старий друже.";
    private static final String NOTE_1 = "looked outside - a plain verb, no idiom";
    private static final String NOTE_4 = "old friend is a warm address, keep ⟦g9⟧ and [1] out of it";
    private static final String FULL_REPLY = "{\"items\":["
            + "{\"id\":\"1\",\"notes\":\"" + NOTE_1 + "\",\"target\":\"" + T1 + "\"},"
            + "{\"id\":\"2\",\"notes\":\"\",\"target\":\"" + T2 + "\"},"
            + "{\"id\":\"3\",\"target\":\"" + T3 + "\"},"
            + "{\"id\":\"4\",\"notes\":\"" + NOTE_4 + "\",\"target\":\"" + T4 + "\"}]}";

    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Logger bookloom;
    private Level levelBefore;

    @BeforeEach
    void turnTheSwitchOnAndListen() {
        System.setProperty(BatchNotesSwitch.PROPERTY_NAME, "on");
        bookloom = (Logger) LoggerFactory.getLogger("ua.bookloom");
        levelBefore = bookloom.getLevel();
        bookloom.setLevel(Level.TRACE);
        events.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        events.start();
        bookloom.addAppender(events);
    }

    @AfterEach
    void restore() {
        bookloom.detachAppender(events);
        bookloom.setLevel(levelBefore);
        System.clearProperty(BatchNotesSwitch.PROPERTY_NAME);
    }

    private static BatchReply parse(final String reply) {
        return PARSER.parse(reply, ITEMS, "en", "uk");
    }

    // IF every entry carries a note before its target, THEN each target is exactly what followed "target", no note in
    // it.
    @Test
    void parse_notesBeforeEachTarget_targetsExcludeTheNotes() {
        final BatchReply reply = parse(FULL_REPLY);

        assertThat(reply.isClean()).isTrue();
        assertThat(reply.acceptedIds()).containsExactly("1", "2", "3", "4");
        assertThat(reply.outcome("1"))
                .hasValueSatisfying(o -> assertThat(o.target()).isEqualTo(T1));
        assertThat(reply.outcome("4"))
                .hasValueSatisfying(o -> assertThat(o.target()).isEqualTo(T4));
    }

    // A note holding a token or a marker must never reach the item checks: the item is judged on its target alone.
    @Test
    void parse_noteHoldingATokenAndAMarker_doesNotFailTheItem() {
        final BatchReply reply = parse(FULL_REPLY);

        assertThat(reply.outcome("4"))
                .hasValueSatisfying(o -> assertThat(o.problems()).isEmpty());
    }

    // The note is not translation: it is gone from every outcome and from the accepted text.
    @Test
    void parse_notes_neverAppearInAnyOutcome() {
        final BatchReply reply = parse(FULL_REPLY);

        assertThat(reply.outcome("1"))
                .hasValueSatisfying(o -> assertThat(o.toString()).doesNotContain(NOTE_1));
        assertThat(reply.outcome("4"))
                .hasValueSatisfying(o -> assertThat(o.toString()).doesNotContain("warm address"));
    }

    // A small model forgets the field on some items: that is no failure.
    @Test
    void parse_someEntriesWithoutNotes_areAcceptedAllTheSame() {
        final String reply = "{\"items\":["
                + "{\"id\":\"1\",\"target\":\"" + T1 + "\"},"
                + "{\"id\":\"2\",\"notes\":\"" + NOTE_1 + "\",\"target\":\"" + T2 + "\"},"
                + "{\"id\":\"3\",\"target\":\"" + T3 + "\"},"
                + "{\"id\":\"4\",\"target\":\"" + T4 + "\"}]}";

        assertThat(parse(reply).isClean()).isTrue();
    }

    // A reply cut inside its last entry keeps the closed ones, notes and all.
    @Test
    void parse_replyWithNotesCutInTheLastEntry_stillSalvagesTheClosedEntries() {
        final String reply = "{\"items\":["
                + "{\"id\":\"1\",\"notes\":\"" + NOTE_1 + "\",\"target\":\"" + T1 + "\"},"
                + "{\"id\":\"2\",\"notes\":\"a plain clause\",\"target\":\"" + T2 + "\"},"
                + "{\"id\":\"3\",\"notes\":\"a year\",\"tar";

        final BatchReply result = parse(reply);

        assertThat(result.acceptedIds()).containsExactly("1", "2");
        assertThat(result.outcome("1"))
                .hasValueSatisfying(o -> assertThat(o.target()).isEqualTo(T1));
    }

    @Test
    void parse_notes_areLoggedAtTraceAndNoHigherLevel() {
        parse(FULL_REPLY);

        assertThat(events.list)
                .filteredOn(event -> event.getFormattedMessage().contains("warm address"))
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.TRACE));
    }

    // IF the switch is off, THEN a note a model wrote anyway is ignored and the parse is today's.
    @Test
    void parse_switchOffAndNotesPresent_readsTheTargetsAsToday() {
        System.setProperty(BatchNotesSwitch.PROPERTY_NAME, "off");

        final BatchReply reply = parse(FULL_REPLY);

        assertThat(reply.isClean()).isTrue();
        assertThat(reply.outcome("1"))
                .hasValueSatisfying(o -> assertThat(o.target()).isEqualTo(T1));
    }

    @Test
    void parse_switchOffAndNoNotes_readsTheTargetsAsToday() {
        System.setProperty(BatchNotesSwitch.PROPERTY_NAME, "off");
        final String reply = "{\"items\":[{\"id\":\"1\",\"target\":\"" + T1 + "\"},{\"id\":\"2\",\"target\":\"" + T2
                + "\"},{\"id\":\"3\",\"target\":\"" + T3 + "\"},{\"id\":\"4\",\"target\":\"" + T4 + "\"}]}";

        assertThat(parse(reply).isClean()).isTrue();
    }
}
