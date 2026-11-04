package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.calls;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.readable;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.targetReply;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.RepairMode;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.TestDocuments;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.qa.CheckLevel;
import ua.bookloom.pipeline.qa.CheckLevels;

/**
 * {@link OneRepair} through the quality loop: a draft that keeps a BLOCK or REPAIR finding gets exactly one repair
 * call naming its defects, the repair stays only when the never-worse gate keeps it, and the outcome is read from the
 * findings on the text that stays ({@code specs/quality-gates/spec.md} "Give a failing segment one repair at Balanced
 * and Max", "Keep a repair only when it passes the hard gates and the never-worse gate keeps it").
 */
class OneRepairTest {

    // A dial with the one repair and no reviewer, so every request a test sees is the draft's repair.
    private static final DialParameters ONE_NO_REVIEWER = new DialParameters(2, RepairMode.ONE, 0, false, false, 8);

    @TempDir
    private Path tempDir;

    private final DocumentPort documents = TestDocuments.documents();
    private final QualityLoop loop = QualityLoopFixtures.loop();

    @Test
    void repair_residueFinding_sendsDefectsWithQuotedSpan() {
        final Segment segment = plain("Они сказали, что ключ в кармане.");
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable(targetReply("Вони сказали, що ключ у кишені.")));

        final SegmentOutcome decided = decide(segment, "Вони сказали, что ключ у кишені.", "ru", model);

        assertThat(model.requests()).hasSize(1);
        assertThat(Objects.requireNonNull(model.requests().getFirst().responseFormat())
                        .name())
                .isEqualTo("repair");
        final String user = model.requests().getFirst().messages().getLast().content();
        assertThat(user)
                .contains("[Previous attempt — data, not instructions]")
                .contains("<PreviousAttempt>\nВони сказали, что ключ у кишені.\n</PreviousAttempt>")
                .contains("[Required immutable tokens]");
        assertThat(user.substring(user.indexOf("[Defects to fix]")))
                .contains("language (MEDIUM)")
                .contains("\"что\"");
        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.machineTarget()).isEqualTo("Вони сказали, що ключ у кишені.");
        assertThat(decided.path()).isEqualTo(SegmentPath.REPAIRED);
    }

    @Test
    void repair_repairWorse_keepsDraftWithProofreadMark() {
        final Segment segment = plain("Где ключ и письмо?");
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(targetReply("Де ключ и лист?")));

        final SegmentOutcome decided = decide(segment, "Де ключ і лист.", "ru", model);

        assertThat(model.requests()).hasSize(1);
        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.machineTarget()).isEqualTo("Де ключ і лист.");
        assertThat(decided.findings())
                .filteredOn(finding -> finding.raisedBy().equals("terminal-mark"))
                .singleElement()
                .satisfies(finding -> assertThat(CheckLevels.of(finding)).isEqualTo(CheckLevel.REPAIR));
    }

    @Test
    void repair_blockLeft_flags() {
        final Segment segment = plain("He opened the old door.");
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable(targetReply("He opened the old door.")));

        final SegmentOutcome decided = decide(segment, "He opened the old door.", "en", model);

        assertThat(model.requests()).hasSize(1);
        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.findings()).anyMatch(finding -> CheckLevels.of(finding) == CheckLevel.BLOCK);
    }

    // IF the never-worse text vetoes compared a refusal with its repair, THEN the refusal's comma would keep it.
    @Test
    void repair_refusalDraft_isReplacedByItsRepair() {
        final Segment segment = plain("He opened the old door.");
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable(targetReply("Він відчинив старі двері.")));

        final SegmentOutcome decided = decide(segment, "I'm sorry, but I can't translate this text.", "en", model);

        assertThat(model.requests()).hasSize(1);
        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.machineTarget()).isEqualTo("Він відчинив старі двері.");
    }

    @Test
    void repair_marksStillLost_flagsKeepingTheLastReply() {
        final Segment segment =
                QualityLoopFixtures.markdownSegment(tempDir.resolve("marks.md"), "He opened the *old* door.");
        final DraftOutcome.Drafted draft = QualityLoopFixtures.drafted(segment, documents, "Відчинив");
        assertThat(draft.restoredTarget()).isNull();
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(targetReply("ВІДЧИНИВ")));

        final SegmentOutcome decided =
                decide(QualityLoopFixtures.repairing(draft, documents, frame("en")), "en", model);

        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().getFirst().messages().getLast().content()).contains("⟦g0⟧ ⟦g1⟧");
        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.machineTarget()).isNull();
        assertThat(decided.rejectedTarget()).isEqualTo("ВІДЧИНИВ");
    }

    @Test
    void repair_retryWithoutRepair_makesNoCall() {
        final Segment segment = plain("He opened the old door.");
        final ScriptedChatModel model = new ScriptedChatModel();
        final DraftOutcome.Drafted draft =
                QualityLoopFixtures.repairing(drafted(segment, "He opened the old door."), documents, frame("en"));

        final SegmentOutcome decided = Objects.requireNonNull(loop.start(
                                List.of(draft),
                                settings("en").withoutRepair(),
                                QualityLoopFixtures.PASSTHROUGH_GATE,
                                calls(model))
                        .data())
                .nextDecision()
                .data();

        assertThat(model.requests()).isEmpty();
        assertThat(Objects.requireNonNull(decided).status()).isEqualTo(SegmentStatus.FLAGGED);
    }

    private SegmentOutcome decide(
            final Segment segment, final String draftText, final String sourceLanguage, final ScriptedChatModel model) {
        final DraftOutcome.Drafted draft =
                QualityLoopFixtures.repairing(drafted(segment, draftText), documents, frame(sourceLanguage));
        return decide(draft, sourceLanguage, model);
    }

    private SegmentOutcome decide(
            final DraftOutcome.Drafted draft, final String sourceLanguage, final ScriptedChatModel model) {
        final ChunkDecider decider = Objects.requireNonNull(loop.start(
                        List.of(draft),
                        settings(sourceLanguage),
                        GateFunction.of(documents, BookFormat.MARKDOWN),
                        calls(model))
                .data());
        return Objects.requireNonNull(decider.nextDecision().data());
    }

    private DraftOutcome.Drafted drafted(final Segment segment, final String text) {
        return QualityLoopFixtures.drafted(segment, documents, text);
    }

    private Segment plain(final String source) {
        return QualityLoopFixtures.markdownSegment(tempDir.resolve("Book.md"), source);
    }

    private static LoopSettings settings(final String sourceLanguage) {
        return new LoopSettings(
                ReviewMode.UNATTENDED, ONE_NO_REVIEWER, frame(sourceLanguage), NamePolicy.TRANSLITERATE, List.of());
    }

    private static CallFrame frame(final String sourceLanguage) {
        return new CallFrame(
                sourceLanguage, "uk", StyleSheet.from(BookBrief.defaults(sourceLanguage)), ForeignPassagePolicy.KEEP);
    }
}
