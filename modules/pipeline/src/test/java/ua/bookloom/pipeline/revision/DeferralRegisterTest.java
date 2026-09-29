package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.judge.JudgeDeferral;

/**
 * Which deferrals a fact leaves behind: a changed locked term on the decided segments holding its previous rendering, a
 * judge's deferral on its segment, and a character of unknown gender on each segment naming it.
 */
class DeferralRegisterTest {

    private static final String PROJECT = "p1";

    static Stream<Arguments> changesThatRecordNothing() {
        return Stream.of(
                Arguments.of("an unlocked entry", term("Hale", "Хейл", false), term("Hale", "Гейл", false)),
                Arguments.of("an entry with no previous target", term("Hale", null, false), term("Hale", "Гейл", true)),
                Arguments.of(
                        "an entry with a blank previous target", term("Hale", " ", true), term("Hale", "Гейл", true)),
                Arguments.of("an unchanged target", term("Hale", "Хейл", true), term("Hale", "Хейл", true)),
                Arguments.of(
                        "an entry that was locked and is now unlocked",
                        term("Hale", "Хейл", true),
                        term("Hale", "Гейл", false)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changesThatRecordNothing")
    void termChanged_changeThatDoesNotQualify_recordsNothing(
            final String description, final GlossaryEntry before, final GlossaryEntry after) {
        final List<SegmentRecord> decided = List.of(decided("ch01.xhtml:3", "Хейл пішов."));

        assertThat(DeferralRegister.termChanged(before, after, decided)).isEmpty();
    }

    @Test
    void termChanged_lockedTargetChanged_recordsOnlyTheSegmentsHoldingThePreviousTargetWholeWord() {
        final List<SegmentRecord> decided = List.of(
                decided("ch01.xhtml:3", "Хейл пішов."),
                decided("ch01.xhtml:4", "Хейлова пішла."),
                decided("ch01.xhtml:5", "Він пішов."));

        final List<Deferral> deferrals =
                DeferralRegister.termChanged(term("Hale", "Хейл", false), term("Hale", "Гейл", true), decided);

        assertThat(deferrals)
                .containsExactly(new Deferral(
                        "p1:ch01.xhtml:3:TERM:Hale",
                        PROJECT,
                        "ch01.xhtml:3",
                        DeferralReason.TERM,
                        "Hale",
                        "Хейл",
                        null,
                        null));
    }

    @Test
    void termChanged_bothTermsChangedInOneSegment_recordsTwoDeferralsOnIt() {
        final List<SegmentRecord> decided = List.of(decided("ch01.xhtml:3", "Хейл і Мілтон пішли."));
        final List<Deferral> deferrals = new ArrayList<>();

        deferrals.addAll(DeferralRegister.termChanged(term("Hale", "Хейл", true), term("Hale", "Гейл", true), decided));
        deferrals.addAll(
                DeferralRegister.termChanged(term("Milton", "Мілтон", true), term("Milton", "Мілтен", true), decided));

        assertThat(deferrals)
                .extracting(Deferral::segmentId, Deferral::waitingOn, Deferral::replacedRendering, Deferral::id)
                .containsExactly(
                        tuple("ch01.xhtml:3", "Hale", "Хейл", "p1:ch01.xhtml:3:TERM:Hale"),
                        tuple("ch01.xhtml:3", "Milton", "Мілтон", "p1:ch01.xhtml:3:TERM:Milton"));
    }

    @Test
    void termChanged_segmentWithAnEditOfItsOwn_isJudgedByTheEditNotTheMachineTarget() {
        final SegmentRecord editedAway =
                decided("ch01.xhtml:3", "Хейл пішов.").withUserTarget("Він пішов.", "Він пішов.");
        final SegmentRecord editedToHold =
                decided("ch01.xhtml:4", "Він пішов.").withUserTarget("Хейл сів.", "Хейл сів.");

        final List<Deferral> deferrals = DeferralRegister.termChanged(
                term("Hale", "Хейл", true), term("Hale", "Гейл", true), List.of(editedAway, editedToHold));

        assertThat(deferrals).extracting(Deferral::segmentId).containsExactly("ch01.xhtml:4");
    }

    @Test
    void termChanged_segmentWithNoTargetYet_recordsNothing() {
        final SegmentRecord pending = decided("ch01.xhtml:3", "x").withMachineTarget(null, null);

        assertThat(DeferralRegister.termChanged(
                        term("Hale", "Хейл", true), term("Hale", "Гейл", true), List.of(pending)))
                .isEmpty();
    }

    @Test
    void fromJudge_deferralOnASegment_becomesAJudgeDeferralWaitingOnTheReason() {
        final List<Deferral> deferrals = DeferralRegister.fromJudge(
                PROJECT, List.of(new JudgeDeferral("ch01.xhtml:9", "whether Sam is a woman")));

        assertThat(deferrals)
                .containsExactly(new Deferral(
                        "p1:ch01.xhtml:9:JUDGE:whether Sam is a woman",
                        PROJECT,
                        "ch01.xhtml:9",
                        DeferralReason.JUDGE,
                        "whether Sam is a woman",
                        null,
                        null,
                        null));
    }

    @Test
    void unknownGender_characterOfUnknownGenderNamed_recordsOneGenderDeferralWaitingOnTheTerm() {
        final List<Deferral> deferrals = DeferralRegister.unknownGender(
                PROJECT, sam(), List.of(entry("Sam", TermType.CHARACTER, Gender.UNKNOWN)));

        assertThat(deferrals)
                .containsExactly(new Deferral(
                        "p1:ch01.xhtml:9:GENDER_UNKNOWN:Sam",
                        PROJECT,
                        "ch01.xhtml:9",
                        DeferralReason.GENDER_UNKNOWN,
                        "Sam",
                        null,
                        null,
                        null));
    }

    static Stream<Arguments> deferralsWaitingOnBookOrModelText() {
        return Stream.of(
                Arguments.of(
                        "a judge deferral",
                        (Supplier<List<Deferral>>) () -> DeferralRegister.fromJudge(
                                PROJECT, List.of(new JudgeDeferral("ch01.xhtml:9", "whether Sam is a woman"))),
                        "whether Sam is a woman",
                        "reason=JUDGE"),
                Arguments.of(
                        "an unknown-gender deferral",
                        (Supplier<List<Deferral>>) () -> DeferralRegister.unknownGender(
                                PROJECT, sam(), List.of(entry("Sam", TermType.CHARACTER, Gender.UNKNOWN))),
                        "Sam",
                        "reason=GENDER_UNKNOWN"));
    }

    // The judge's words and a character's name are model and book text, which the log holds at TRACE only.
    @ParameterizedTest(name = "{0}")
    @MethodSource("deferralsWaitingOnBookOrModelText")
    void deferralRecorded_textWaitedOn_isLoggedAtTraceOnly(
            final String description,
            final Supplier<List<Deferral>> producer,
            final String waitingOn,
            final String reason) {
        final List<ILoggingEvent> lines = loggedAtTrace(producer);

        assertThat(lines)
                .filteredOn(line -> line.getLevel().isGreaterOrEqual(Level.DEBUG))
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(line -> line.contains(waitingOn))
                .anyMatch(line -> line.contains("segmentId=ch01.xhtml:9") && line.contains(reason));
        assertThat(lines)
                .filteredOn(line -> line.getLevel() == Level.TRACE)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(line -> line.contains(waitingOn));
    }

    static Stream<Arguments> glossariesAndTheTermsWaitedOn() {
        return Stream.of(
                Arguments.of(
                        "two characters of unknown gender",
                        List.of(
                                entry("Sam", TermType.CHARACTER, Gender.UNKNOWN),
                                entry("Alex", TermType.CHARACTER, Gender.UNKNOWN)),
                        List.of("Sam", "Alex")),
                Arguments.of(
                        "a character known to be female",
                        List.of(entry("Sam", TermType.CHARACTER, Gender.FEMALE)),
                        List.of()),
                Arguments.of(
                        "a place of unknown gender",
                        List.of(entry("Harbour", TermType.PLACE, Gender.UNKNOWN)),
                        List.of()),
                Arguments.of(
                        "a character named only inside a longer word",
                        List.of(entry("Sa", TermType.CHARACTER, Gender.UNKNOWN)),
                        List.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("glossariesAndTheTermsWaitedOn")
    void unknownGender_glossary_recordsOneDeferralPerUnknownCharacterNamed(
            final String description, final List<GlossaryEntry> glossary, final List<String> waitingOn) {
        final List<Deferral> deferrals = DeferralRegister.unknownGender(PROJECT, sam(), glossary);

        assertThat(deferrals).allMatch(deferral -> deferral.reason() == DeferralReason.GENDER_UNKNOWN);
        assertThat(deferrals).extracting(Deferral::waitingOn).containsExactlyElementsOf(waitingOn);
    }

    private static List<ILoggingEvent> loggedAtTrace(final Supplier<List<Deferral>> producer) {
        final Logger logger = (Logger) LoggerFactory.getLogger(DeferralRegister.class);
        final Level previous = logger.getLevel();
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.TRACE);
        try {
            assertThat(producer.get()).hasSize(1);
            return List.copyOf(appender.list);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
            appender.stop();
        }
    }

    /** A segment naming Sam and Alex at the Harbour, with a footnote marker glued to Sam's name. */
    private static Segment sam() {
        final String masked = "Sam⟦g0⟧ met Alex at the Harbour.";
        return new Segment(
                "ch01.xhtml:9",
                "ch01.xhtml",
                9,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                Map.of("g0", "<sup>1</sup>"),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, masked.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    private static GlossaryEntry entry(final String term, final TermType type, final Gender gender) {
        return new GlossaryEntry(
                PROJECT + ":" + term.toLowerCase(Locale.ROOT), PROJECT, term, null, type, gender, false);
    }

    private static GlossaryEntry term(final String term, @Nullable final String target, final boolean locked) {
        return new GlossaryEntry(
                PROJECT + ":" + term.toLowerCase(Locale.ROOT),
                PROJECT,
                term,
                target,
                TermType.CHARACTER,
                Gender.MALE,
                locked);
    }

    private static SegmentRecord decided(final String segmentId, final String target) {
        return new SegmentRecord(
                PROJECT,
                segmentId,
                "ch01.xhtml",
                0,
                SegmentKind.PARAGRAPH,
                SegmentStatus.ACCEPTED,
                target,
                target,
                null,
                null,
                0.9,
                null,
                List.of(),
                SegmentPath.DRAFT,
                0,
                false,
                null);
    }
}
