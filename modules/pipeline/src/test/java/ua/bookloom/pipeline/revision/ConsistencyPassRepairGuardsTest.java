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
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.review.ReviewFixtures;

/**
 * The consistency pass never makes a clean text worse (15i.T2): a reply that brings a token into a paragraph that has
 * none or a Russian letter into a Ukrainian one is refused, and a new draft of a doubted text that mends a mixed-script
 * word is not refused for the one dash it drops.
 */
class ConsistencyPassRepairGuardsTest {

    private static final PassOptions EXPORT = new PassOptions(true, false);
    private static final ContextSnapshot SNAPSHOT =
            new ContextSnapshot(List.of("Ранок."), List.of(), List.of(), null, "Keep the narrator's voice.");
    private static final String BEFORE = "«Сем зустрів Хейла.»";
    // The "o" after "п" is a Latin letter: one word written in two alphabets, as the model drafts it.
    private static final String MIXED_TWO_DASHES = "— Біжи, Денні, — сказав я. Він пoбіг.";
    private static final String MENDED_ONE_DASH = "— Біжи, Денні, сказав я. Він побіг.";
    private static final String CLEAN_TWO_DASHES = "— Біжи, Денні, — сказав я. Він побіг.";
    // A Russian letter in the word "стары"; the redraft below writes it right and drops the second dash.
    private static final String RUSSIAN_TWO_DASHES = "— Біжи, Денні, — сказав я. Він побіг стары.";
    private static final String RUSSIAN_MENDED_ONE_DASH = "— Біжи, Денні, сказав я. Він побіг старі.";
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

    // IF a re-render that adds a token the paragraph never had were kept, THEN the document gate would drop the
    // paragraph's markup at export while the deferral looked closed.
    @Test
    void run_genderRevisionAddsATokenTheParagraphLacks_keepsTheOldTextAndTheDeferral() {
        book.add(book.character("Sam", "Сем", Gender.UNKNOWN, false));
        book.decide(SAM_DOOR, DOOR_PLAIN, DOOR_MASKED);
        book.recordUnknownGender(SAM_DOOR);
        book.glossaryUpdate(book.character("Sam", "Сем", Gender.FEMALE, false));
        book.model().answerTo(REVISION, reply("Сем відчинила ⟦g0⟧старі⟦g1⟧ ⟦g2⟧двері.⟦g3⟧"));

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

        assertThat(book.stored(RUN_DANNY).machineTarget()).isEqualTo(MENDED_ONE_DASH);
        assertThat(book.stored(RUN_DANNY).status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(report.checks().retriedImproved()).isEqualTo(1);
    }

    // IF the same relaxation did not cover an alphabet finding, THEN a Russian letter would stay in the book because
    // the new text lost a dash while losing the letter.
    @Test
    void run_retryThatMendsARussianLetterAndDropsOneDash_keepsTheNewText() {
        flagged(RUN_DANNY, RUSSIAN_TWO_DASHES);
        book.model().answerTo(DRAFT, reply(RUSSIAN_MENDED_ONE_DASH));
        book.model().answerTo(NEIGHBOUR_ASKED_FOR, reply(RUSSIAN_MENDED_ONE_DASH));

        final ConsistencyReport report = ok(book.runExport(EXPORT));

        assertThat(book.stored(RUN_DANNY).machineTarget()).isEqualTo(RUSSIAN_MENDED_ONE_DASH);
        assertThat(book.stored(RUN_DANNY).status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(report.checks().retriedImproved()).isEqualTo(1);
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

    // IF the mode counted a dash before it looked at what the redraft mended, THEN the mended word is thrown away.
    // The caller (PassChecks) names the mended mode; the alphabet case needs the target language and is covered whole
    // by run_retryThatMendsARussianLetterAndDropsOneDash_keepsTheNewText.
    @Test
    void violation_mendedRedraftThatDropsOneDash_isAdmitted() {
        assertThat(RevisionGuards.violation(MIXED_TWO_DASHES, MENDED_ONE_DASH, RevisionGuards.Mode.MENDED))
                .isEmpty();
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

    // IF the one-mark allowance stopped at dashes, THEN a mended word would be lost whenever the model also dropped a
    // closing quote mark.
    @Test
    void violation_mendedRedraftThatDropsOneQuoteMark_isAdmitted() {
        assertThat(RevisionGuards.violation(
                        "«Біжи, Денні», — сказав я. Він пoбіг.",
                        "«Біжи, Денні, — сказав я. Він побіг.",
                        RevisionGuards.Mode.MENDED))
                .isEmpty();
    }

    @Test
    void violation_noLossRedraftThatMendsNothingAndDropsOneQuoteMark_namesTheQuotes() {
        assertThat(RevisionGuards.violation(
                        "«Біжи, Денні», — сказав я. Він побіг.",
                        "«Біжи, Денні, — сказав я. Він побіг.",
                        RevisionGuards.Mode.NO_LOSS))
                .contains("quotes");
    }

    // The allowance is exactly one mark: a redraft that mends the word and drops two dashes is still refused.
    @Test
    void violation_mendedRedraftThatDropsTwoDashes_namesTheDashes() {
        assertThat(RevisionGuards.violation(
                        "— Біжи, Денні, — сказав я. — Він пoбіг.",
                        "— Біжи, Денні, сказав я. Він побіг.",
                        RevisionGuards.Mode.MENDED))
                .contains("dashes");
    }

    // One mark in all: a dash and a quote mark together are two.
    @Test
    void violation_mendedRedraftThatDropsADashAndAQuoteMark_namesTheQuotes() {
        assertThat(RevisionGuards.violation(
                        "«Біжи, Денні», — сказав я. Він пoбіг.",
                        "«Біжи, Денні, сказав я. Він побіг.",
                        RevisionGuards.Mode.MENDED))
                .contains("quotes");
    }
}
