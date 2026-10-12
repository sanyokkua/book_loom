package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.revision.RevisionBook.DOOR_MASKED;
import static ua.bookloom.pipeline.revision.RevisionBook.DOOR_PLAIN;
import static ua.bookloom.pipeline.revision.RevisionBook.RUN_DANNY;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_DOOR;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_MET_HALE;
import static ua.bookloom.pipeline.revision.RevisionBook.ok;
import static ua.bookloom.pipeline.revision.RevisionBook.reply;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.KnownRed;
import ua.bookloom.pipeline.review.ReviewFixtures;

/**
 * The consistency pass never makes a clean text worse (15i.T2): a reply that brings a token into a paragraph that has
 * none or a Russian letter into a Ukrainian one is refused, and a new draft of a doubted text that mends a mixed-script
 * word is not refused for the one dash it drops. A case that is still wrong today sits in {@link KnownRed#until}, so
 * the day the task lands the test says to drop the wrapper; the cases without it must hold before and after.
 */
class ConsistencyPassRepairGuardsTest {

    private static final String T2 = "15i.T2";
    private static final PassOptions EXPORT = new PassOptions(true, false);
    private static final ContextSnapshot SNAPSHOT =
            new ContextSnapshot(List.of("Ранок."), List.of(), List.of(), null, "Keep the narrator's voice.");
    private static final String BEFORE = "«Сем зустрів Хейла.»";
    // The "o" after "п" is a Latin letter: one word written in two alphabets, as the model drafts it.
    private static final String MIXED_TWO_DASHES = "— Біжи, Денні, — сказав я. Він пoбіг.";
    private static final String MENDED_ONE_DASH = "— Біжи, Денні, сказав я. Він побіг.";
    private static final String CLEAN_TWO_DASHES = "— Біжи, Денні, — сказав я. Він побіг.";
    private static final String NEIGHBOUR_ASKED_FOR = "consistency";
    private static final String REVISION = "revision";
    private static final String DRAFT = "draft";

    @TempDir
    private Path tempDir;

    private RevisionBook book;

    @BeforeEach
    void openBook() {
        book = RevisionBook.open(tempDir);
        book.add(book.character("Hale", "Гейл", Gender.MALE, false));
        book.model().answerTo("reviewer", Result.ok(new ChatResponse("{\"results\":[]}", FinishReason.STOP)));
    }

    private void repaired(final String segmentId, final String text) {
        ReviewFixtures.update(
                book.desk(),
                segmentId,
                record -> record.withStatus(SegmentStatus.ACCEPTED)
                        .withMachineTarget(text, text)
                        .withPath(SegmentPath.REPAIRED));
    }

    private void flagged(final String segmentId, final String text) {
        ReviewFixtures.update(
                book.desk(),
                segmentId,
                record -> record.withStatus(SegmentStatus.FLAGGED)
                        .withMachineTarget(text, text)
                        .withFindings(List.of(new QaFinding("script", Severity.HIGH, "a mixed word", "mixed-script"))));
        ReviewFixtures.withContext(book.desk(), segmentId, SNAPSHOT);
    }

    // IF the neighbour check could bring a token into a paragraph that holds none, THEN an invented ⟦g0⟧ would reach
    // the export and the document gate would drop the whole paragraph's markup.
    @Test
    void run_neighbourReplyInventsATokenInATokenlessParagraph_keepsTheOldText() {
        repaired(SAM_MET_HALE, BEFORE);
        book.model().answerTo(NEIGHBOUR_ASKED_FOR, reply("«Сем зустрів ⟦g0⟧Гейла⟦g1⟧.»"));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(book.stored(SAM_MET_HALE).machineTarget()).isEqualTo(BEFORE);
        assertThat(report.neighbourFixes()).isZero();
    }

    // IF a Russian letter in a neighbour fix were kept, THEN the pass would turn a clean Ukrainian paragraph worse.
    @Test
    void run_neighbourReplyBringsARussianLetter_keepsTheOldText() {
        repaired(SAM_MET_HALE, BEFORE);
        book.model().answerTo(NEIGHBOUR_ASKED_FOR, reply("«Сем зустрів Гейлы.»"));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(book.stored(SAM_MET_HALE).machineTarget()).isEqualTo(BEFORE);
        assertThat(report.neighbourFixes()).isZero();
    }

    // IF a gender re-render that writes a Russian letter were kept, THEN a deferral would be closed by a worse text.
    @Test
    void run_genderRevisionBringsARussianLetter_keepsTheOldTextAndTheDeferral() {
        book.add(book.character("Sam", "Сем", Gender.UNKNOWN, false));
        book.decide(SAM_DOOR, DOOR_PLAIN, DOOR_MASKED);
        book.recordUnknownGender(SAM_DOOR);
        book.glossaryUpdate(book.character("Sam", "Сем", Gender.FEMALE, false));
        book.model().answerTo(REVISION, reply("Сем відчинила ⟦g0⟧стары⟦g1⟧ двері."));

        ok(book.run(true));

        assertThat(book.stored(SAM_DOOR).maskedMachineTarget()).isEqualTo(DOOR_MASKED);
        assertThat(book.openDeferrals()).hasSize(1);
    }

    // IF a new draft of a doubted text that mends a mixed-script word were refused for one dash, THEN the word stays
    // broken in the book although the model fixed it.
    @Test
    void run_retryThatMendsAMixedScriptWordAndDropsOneDash_keepsTheNewText() {
        flagged(RUN_DANNY, MIXED_TWO_DASHES);
        book.model().answerTo(DRAFT, reply(MENDED_ONE_DASH));
        book.model().answerTo(NEIGHBOUR_ASKED_FOR, reply(MENDED_ONE_DASH));

        final ConsistencyReport report = ok(book.runExport(EXPORT));

        KnownRed.until(T2, "redraft-mixed-script-one-dash", () -> {
            assertThat(book.stored(RUN_DANNY).machineTarget()).isEqualTo(MENDED_ONE_DASH);
            assertThat(book.stored(RUN_DANNY).status()).isEqualTo(SegmentStatus.REVISED);
            assertThat(report.checks().retriedImproved()).isEqualTo(1);
        });
    }

    // The relaxation is for a mended finding only: a new draft that merely loses the dash stays refused.
    @Test
    void run_retryThatOnlyDropsADash_keepsTheOldText() {
        flagged(RUN_DANNY, CLEAN_TWO_DASHES);
        book.model().answerTo(DRAFT, reply(MENDED_ONE_DASH));
        book.model().answerTo(NEIGHBOUR_ASKED_FOR, reply(CLEAN_TWO_DASHES));

        final ConsistencyReport report = ok(book.runExport(EXPORT));

        assertThat(book.stored(RUN_DANNY).machineTarget()).isEqualTo(CLEAN_TWO_DASHES);
        assertThat(report.checks().retriedImproved()).isZero();
    }

    // IF NO_LOSS counted a dash before it looked at what the redraft mended, THEN the mended word is thrown away.
    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "mixed-script word|— Біжи, Денні, — сказав я. Він пoбіг.|— Біжи, Денні, сказав я. Він побіг.",
                "Russian letter|— Я відчинив стары двері, — сказав він.|— Я відчинив старі двері, сказав він."
            })
    void violation_noLossRedraftThatMendsAFindingAndDropsOneDash_isAdmitted(
            final String mended, final String before, final String after) {
        KnownRed.until(
                T2,
                "no-loss-" + mended,
                () -> assertThat(RevisionGuards.violation(before, after, RevisionGuards.Mode.NO_LOSS))
                        .isEmpty());
    }

    @Test
    void violation_noLossRedraftThatMendsNothingAndDropsOneDash_namesTheDashes() {
        assertThat(RevisionGuards.violation(CLEAN_TWO_DASHES, MENDED_ONE_DASH, RevisionGuards.Mode.NO_LOSS))
                .contains("dashes");
    }

    @Test
    void violation_noLossRedraftThatMendsAFindingAndKeepsEveryDash_isAdmitted() {
        assertThat(RevisionGuards.violation(MIXED_TWO_DASHES, CLEAN_TWO_DASHES, RevisionGuards.Mode.NO_LOSS))
                .isEmpty();
    }
}
