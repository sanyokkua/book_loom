package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.revision.RevisionBook.DOOR_MASKED;
import static ua.bookloom.pipeline.revision.RevisionBook.DOOR_PLAIN;
import static ua.bookloom.pipeline.revision.RevisionBook.DOOR_REVISED;
import static ua.bookloom.pipeline.revision.RevisionBook.DOOR_REVISED_PLAIN;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_DOOR;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_LEFT;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_MET_HALE;
import static ua.bookloom.pipeline.revision.RevisionBook.ok;
import static ua.bookloom.pipeline.revision.RevisionBook.reply;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.judge.JudgeDeferral;
import ua.bookloom.pipeline.review.ReviewFixtures;

/**
 * The consistency pass's revision calls over a real opened EPUB: a character whose gender became known is re-rendered
 * through one call per segment, a reply that breaks the markup or cannot be read changes nothing, a judge deferral is
 * left alone, and a segment the person edited only gets a proposal that the real review desk applies on acceptance.
 */
class ConsistencyPassGenderTest {

    private static final String REVISION = "revision";

    @TempDir
    private Path tempDir;

    private RevisionBook book;

    @BeforeEach
    void openBook() {
        book = RevisionBook.open(tempDir);
    }

    // A character whose gender became known is re-rendered by one revision call over the masked target.
    @Test
    void run_genderNowKnown_reRendersThroughOneCall() {
        samDoorWaitingOnGender();
        book.model().answerTo(REVISION, reply(DOOR_REVISED));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(book.model().requests()).hasSize(1);
        final String user = book.userMessage(0);
        assertThat(user.split("<Text>", -1)).hasSize(2);
        assertThat(user).contains("<Text>\n" + DOOR_MASKED + "\n</Text>", "[Source]\nSam opened the ⟦g0⟧old⟦g1⟧ door.");
        assertThat(user).contains("- Sam (Сем): female");
        assertThat(book.model().requests().getFirst().temperature()).isEqualTo(0.2);
        final SegmentRecord record = book.stored(SAM_DOOR);
        assertThat(record.maskedMachineTarget()).isEqualTo(DOOR_REVISED);
        assertThat(record.machineTarget()).isEqualTo(DOOR_REVISED_PLAIN);
        assertThat(record.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(report).isEqualTo(new ConsistencyReport(0, 1, 0, List.of("ch1 · p10: revised for gender")));
        assertThat(book.openDeferrals()).isEmpty();
    }

    // A gender re-render fixes no recorded finding, so a FLAGGED segment takes the new target and stays FLAGGED.
    @Test
    void run_flaggedSegmentReRenderedForGender_takesTheTargetAndStaysFlagged() {
        final QaFinding glossary = new QaFinding("glossary", Severity.MEDIUM, "note", "glossary");
        samDoorWaitingOnGender();
        ReviewFixtures.update(
                book.desk(),
                SAM_DOOR,
                record -> record.withStatus(SegmentStatus.FLAGGED).withFindings(List.of(glossary)));
        book.model().answerTo(REVISION, reply(DOOR_REVISED));

        ok(book.run(true));

        assertThat(book.stored(SAM_DOOR))
                .extracting(SegmentRecord::maskedMachineTarget, SegmentRecord::status, SegmentRecord::findings)
                .containsExactly(DOOR_REVISED, SegmentStatus.FLAGGED, List.of(glossary));
        assertThat(book.openDeferrals()).isEmpty();
    }

    // Two unknown-gender characters in one segment share its one revision call.
    @Test
    void run_twoGenderDeferralsOnOneSegment_shareOneCall() {
        book.add(book.character("Sam", "Сем", Gender.UNKNOWN, false));
        book.add(book.character("Hale", "Хейл", Gender.UNKNOWN, false));
        book.decide(SAM_MET_HALE, "Сем зустрів Хейла.", "Сем зустрів Хейла.");
        book.recordUnknownGender(SAM_MET_HALE);
        book.glossaryUpdate(book.character("Sam", "Сем", Gender.FEMALE, false));
        book.glossaryUpdate(book.character("Hale", "Хейл", Gender.MALE, false));
        book.model().answerTo(REVISION, reply("Сем зустріла Хейла."));

        ok(book.run(true));

        assertThat(book.model().requests()).hasSize(1);
        assertThat(book.userMessage(0)).contains("- Sam (Сем): female\n- Hale (Хейл): male");
        assertThat(book.stored(SAM_MET_HALE).machineTarget()).isEqualTo("Сем зустріла Хейла.");
        assertThat(book.openDeferrals()).isEmpty();
    }

    // A revision reply that drops a placeholder fails the document gate, so nothing changes and the deferral waits.
    @Test
    void run_revisionDropsPlaceholder_keepsSegmentAndDeferral() {
        samDoorWaitingOnGender();
        book.model().answerTo(REVISION, reply("Сем відчинила ⟦g0⟧старі двері."));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(book.stored(SAM_DOOR).maskedMachineTarget()).isEqualTo(DOOR_MASKED);
        assertThat(book.stored(SAM_DOOR).status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(report).isEqualTo(new ConsistencyReport(0, 0, 0, List.of()));
        assertThat(book.openDeferrals())
                .extracting(Deferral::segmentId, Deferral::reason)
                .containsExactly(tuple(SAM_DOOR, DeferralReason.GENDER_UNKNOWN));
    }

    // A revision that echoes the English source fails a soft check outright, so nothing changes.
    @Test
    void run_revisionEchoesSource_keepsSegmentAndDeferral() {
        samDoorWaitingOnGender();
        book.model().answerTo(REVISION, reply("Sam opened the ⟦g0⟧old⟦g1⟧ door."));

        ok(book.run(true));

        assertThat(book.stored(SAM_DOOR).maskedMachineTarget()).isEqualTo(DOOR_MASKED);
        assertThat(book.openDeferrals()).hasSize(1);
    }

    // A reply the pass cannot read leaves the segment as it was and the deferral open, and the pass goes on.
    @Test
    void run_emptyCompletion_keepsSegmentAndDeferral() {
        samDoorWaitingOnGender();
        book.model().answerTo(REVISION, Result.err(AppError.of(ErrorCode.emptyCompletion, "Empty", "No content.")));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(book.stored(SAM_DOOR).maskedMachineTarget()).isEqualTo(DOOR_MASKED);
        assertThat(report.genderReRenders()).isZero();
        assertThat(book.openDeferrals()).hasSize(1);
    }

    // A provider failure other than an unreadable reply ends the pass with that error for the caller to route.
    @Test
    void run_providerUnreachable_endsWithThatError() {
        samDoorWaitingOnGender();
        book.model().answerTo(REVISION, Result.err(AppError.of(ErrorCode.unreachable, "Down", "No server.")));

        final Result<ConsistencyReport> result = book.run(true);

        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.unreachable);
        assertThat(book.stored(SAM_DOOR).maskedMachineTarget()).isEqualTo(DOOR_MASKED);
        assertThat(book.openDeferrals()).hasSize(1);
    }

    // A character whose gender is still unknown is not re-rendered.
    @Test
    void run_genderStillUnknown_makesNoCall() {
        book.add(book.character("Sam", "Сем", Gender.UNKNOWN, false));
        book.decide(SAM_DOOR, DOOR_PLAIN, DOOR_MASKED);
        book.recordUnknownGender(SAM_DOOR);

        ok(book.run(true));

        assertThat(book.model().requests()).isEmpty();
        assertThat(book.openDeferrals()).hasSize(1);
    }

    // A judge deferral is recorded only: the pass neither calls the model for it nor resolves it.
    @Test
    void run_judgeDeferral_untouchedAndNoCall() {
        book.decide(SAM_DOOR, DOOR_PLAIN, DOOR_MASKED);
        DeferralRegister.fromJudge(book.desk().projectId(), List.of(new JudgeDeferral(SAM_DOOR, "who keeps the light")))
                .forEach(book::recordDeferral);

        ok(book.run(true));

        assertThat(book.model().requests()).isEmpty();
        assertThat(book.stored(SAM_DOOR).status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(book.openDeferrals()).extracting(Deferral::reason).containsExactly(DeferralReason.JUDGE);
    }

    // The person's edit is never overwritten: the re-render waits as a proposal until the desk applies it.
    @Test
    void run_personEditedGenderSegment_proposalAppliedOnlyOnAcceptance() {
        book.add(book.character("Sam", "Сем", Gender.UNKNOWN, false));
        book.edit(SAM_LEFT, "Вона пішла.", "Вона пішла.");
        book.recordUnknownGender(SAM_LEFT);
        book.glossaryUpdate(book.character("Sam", "Сем", Gender.MALE, false));
        book.model().answerTo(REVISION, reply("Він пішов."));

        ok(book.run(true));

        assertThat(book.userMessage(0)).contains("<Text>\nВона пішла.\n</Text>");
        assertThat(book.stored(SAM_LEFT).userTarget()).isEqualTo("Вона пішла.");
        assertThat(book.openDeferrals())
                .extracting(Deferral::proposal, Deferral::maskedProposal)
                .containsExactly(tuple("Він пішов.", "Він пішов."));

        final SegmentRecord applied = ok(book.desk()
                .reviewDesk(ReviewMode.UNATTENDED)
                .acceptProposal(book.desk().projectId(), SAM_LEFT));

        assertThat(applied.userTarget()).isEqualTo("Він пішов.");
        assertThat(applied.maskedUserTarget()).isEqualTo("Він пішов.");
        assertThat(applied.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(applied.reviewed()).isTrue();
        assertThat(book.openDeferrals()).isEmpty();
    }

    // A proposal for a formatted segment keeps its placeholders, so the accepted text can be edited again.
    @Test
    void run_personEditedFormattedSegment_proposalKeepsPlaceholders() {
        book.add(book.character("Sam", "Сем", Gender.UNKNOWN, false));
        book.edit(SAM_DOOR, DOOR_PLAIN, DOOR_MASKED);
        book.recordUnknownGender(SAM_DOOR);
        book.glossaryUpdate(book.character("Sam", "Сем", Gender.FEMALE, false));
        book.model().answerTo(REVISION, reply(DOOR_REVISED));

        ok(book.run(true));

        assertThat(book.stored(SAM_DOOR).maskedUserTarget()).isEqualTo(DOOR_MASKED);
        assertThat(book.openDeferrals())
                .extracting(Deferral::proposal, Deferral::maskedProposal)
                .containsExactly(tuple(DOOR_REVISED_PLAIN, DOOR_REVISED));
    }

    // A term renamed after a gender proposal is swept into that proposal: one proposal waits and holds both fixes.
    @Test
    void run_termRenamedAfterGenderProposal_oneProposalHoldsBothFixes() {
        book.add(book.character("Sam", "Сем", Gender.UNKNOWN, true));
        book.edit(SAM_DOOR, DOOR_PLAIN, DOOR_MASKED);
        book.recordUnknownGender(SAM_DOOR);
        final GlossaryEntry female = book.character("Sam", "Сем", Gender.FEMALE, true);
        book.glossaryUpdate(female);
        book.model().answerTo(REVISION, reply(DOOR_REVISED)).answerTo(REVISION, reply(DOOR_REVISED));
        ok(book.run(true));
        book.change(female, book.character("Sam", "Саманта", Gender.FEMALE, true));

        ok(book.run(true));

        assertThat(book.model().requests()).hasSize(1);
        assertThat(book.openDeferrals())
                .filteredOn(deferral -> deferral.proposal() != null)
                .extracting(Deferral::proposal, Deferral::maskedProposal)
                .containsExactly(
                        tuple("Саманта відчинила <em>старі</em> двері.", "Саманта відчинила ⟦g0⟧старі⟦g1⟧ двері."));
        final SegmentRecord applied = ok(book.desk()
                .reviewDesk(ReviewMode.UNATTENDED)
                .acceptProposal(book.desk().projectId(), SAM_DOOR));
        assertThat(applied.maskedUserTarget()).isEqualTo("Саманта відчинила ⟦g0⟧старі⟦g1⟧ двері.");
        assertThat(book.openDeferrals())
                .filteredOn(deferral -> deferral.segmentId().equals(SAM_DOOR))
                .isEmpty();
    }

    private void samDoorWaitingOnGender() {
        book.add(book.character("Sam", "Сем", Gender.UNKNOWN, false));
        book.decide(SAM_DOOR, DOOR_PLAIN, DOOR_MASKED);
        book.recordUnknownGender(SAM_DOOR);
        book.glossaryUpdate(book.character("Sam", "Сем", Gender.FEMALE, false));
    }
}
