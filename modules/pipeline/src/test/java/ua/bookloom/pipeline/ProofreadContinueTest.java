package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.ProofreadProjects.character;
import static ua.bookloom.pipeline.ProofreadProjects.decide;
import static ua.bookloom.pipeline.ProofreadProjects.decideAll;
import static ua.bookloom.pipeline.ProofreadProjects.decided;
import static ua.bookloom.pipeline.ProofreadProjects.lines;
import static ua.bookloom.pipeline.ProofreadProjects.locked;
import static ua.bookloom.pipeline.ProofreadProjects.ok;
import static ua.bookloom.pipeline.ProofreadProjects.openDeferrals;
import static ua.bookloom.pipeline.ProofreadProjects.update;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.ProofreadOutcome;
import ua.bookloom.api.project.ProofreadVerdict;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.dial.DialParameterProfiles;
import ua.bookloom.pipeline.proofread.DeferralRegister;
import ua.bookloom.pipeline.proofread.ProofreadTallies;
import ua.bookloom.pipeline.review.SegmentActions;
import ua.bookloom.pipeline.run.RunStores;

/**
 * What a finished project offers to continue (design D15 "Resume"): the tally reads a window as read by the very
 * fingerprint the run compares, so a late fact after a finished run is offered and, once read, no longer is; the
 * code-only work a glossary change leaves is offered at every dial; and a segment the person undid is not rewritten
 * again by the next Continue. Invented en→uk lines, every segment decided before the run.
 */
class ProofreadContinueTest {

    private static final String PROOFREAD = "proofread";
    private static final int SIXTEEN = 16;
    private static final String HALE_LEFT = "Хейл пішов.";

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // IF the tally counted a window read by its round alone, THEN a gender set after a finished Max run would leave
    // nothing to continue until a restart.
    @Test
    void tally_genderSetAfterFinishedMaxRun_offeredThenReadByTheContinue() {
        final TestProject project = book(QualityDial.MAX);
        final GlossaryEntry hale = ok(project.stores().glossary().add(character(project, "Hale", "Хейл", Gender.MALE)));
        assertThat(report(job(project, new ScriptedChatModel()).run()).end()).isEqualTo(JobState.COMPLETED);
        assertThat(tallies(project).tally(project.id(), 2).data()).isEmpty();
        final GlossaryEntry female = hale.withGender(Gender.FEMALE);
        ok(project.stores().glossary().update(female));
        DeferralRegister.changed(hale, female, decided(project), Set.of("Book.md:0"), "uk")
                .forEach(deferral -> ok(project.deferrals().add(deferral)));

        assertThat(tallies(project).tally(project.id(), 2).data()).isPresent();
        final ScriptedChatModel continued = new ScriptedChatModel();
        assertThat(report(job(project, continued).run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(proofreadCalls(continued)).isPositive();
        assertThat(tallies(project).tally(project.id(), 2).data()).isEmpty();
    }

    // IF a kept rewrite stamped its window as planned before the rewrite, THEN the mark the rewrite answered would
    // reshape the window (here 0..1 becomes 1) and the next Continue would offer, and read, the same paragraphs again.
    @Test
    void tally_keptGenderRewriteReshapesItsWindow_nothingOfferedAfterTheContinue() {
        final TestProject project = book(QualityDial.BALANCED);
        final String russianLetter = "Дзвін ударыв 1 разів на світанку.";
        update(
                project,
                "Book.md:1",
                record -> record.withStatus(SegmentStatus.FLAGGED).withMachineTarget(russianLetter, russianLetter));
        final GlossaryEntry hale = ok(project.stores().glossary().add(character(project, "Hale", "Хейл", Gender.MALE)));
        assertThat(report(job(project, new ScriptedChatModel()).run()).end()).isEqualTo(JobState.COMPLETED);
        final GlossaryEntry female = hale.withGender(Gender.FEMALE);
        ok(project.stores().glossary().update(female));
        DeferralRegister.changed(hale, female, decided(project), Set.of("Book.md:0"), "uk")
                .forEach(deferral -> ok(project.deferrals().add(deferral)));
        final ScriptedChatModel gendered = new ScriptedChatModel()
                .answerTo(
                        PROOFREAD,
                        Result.ok(new ChatResponse(
                                "{\"items\":[{\"id\":\"Book.md:0\",\"target\":\"Хейл пішла.\"}]}", FinishReason.STOP)));
        assertThat(report(job(project, gendered).run()).end()).isEqualTo(JobState.COMPLETED);
        assertThat(stored(project, "Book.md:0").machineTarget()).isEqualTo("Хейл пішла.");

        assertThat(tallies(project).tally(project.id(), 2).data()).isEmpty();
        final ScriptedChatModel again = new ScriptedChatModel();
        assertThat(report(job(project, again).run()).end()).isEqualTo(JobState.COMPLETED);
        assertThat(proofreadCalls(again)).isZero();
    }

    // IF only proofread windows counted as work left, THEN a renamed target after a Fast run would never be swept.
    @Test
    void codeWorkLeft_termRenamedAfterFastRun_trueUntilTheContinueSweepsIt() {
        final TestProject project = book(QualityDial.FAST);
        assertThat(report(job(project, new ScriptedChatModel()).run()).end()).isEqualTo(JobState.COMPLETED);
        assertThat(ok(tallies(project).codeWorkLeft(project.id()))).isFalse();
        final GlossaryEntry hale = locked(project, "Hale", "Хейл");
        ok(project.stores().glossary().add(hale));
        final GlossaryEntry renamed = locked(project, "Hale", "Гейл");
        ok(project.stores().glossary().update(renamed));
        DeferralRegister.termChanged(hale, renamed, decided(project))
                .forEach(deferral -> ok(project.deferrals().add(deferral)));

        assertThat(ok(tallies(project).codeWorkLeft(project.id()))).isTrue();
        assertThat(report(job(project, new ScriptedChatModel()).run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(stored(project, "Book.md:0").machineTarget()).isEqualTo("Гейл пішов.");
        assertThat(ok(tallies(project).codeWorkLeft(project.id()))).isFalse();
    }

    // IF a renamed target already swept into a proposal still counted as sweep work, THEN a person-edited segment's
    // waiting proposal would offer Continue after every Continue until the person applied it.
    @Test
    void codeWorkLeft_renameSweptIntoAProposal_falseOnceTheProposalWaits() {
        final TestProject project = book(QualityDial.FAST);
        update(project, "Book.md:0", record -> record.withUserTarget(HALE_LEFT, HALE_LEFT));
        assertThat(report(job(project, new ScriptedChatModel()).run()).end()).isEqualTo(JobState.COMPLETED);
        final GlossaryEntry hale = locked(project, "Hale", "Хейл");
        ok(project.stores().glossary().add(hale));
        final GlossaryEntry renamed = locked(project, "Hale", "Гейл");
        ok(project.stores().glossary().update(renamed));
        DeferralRegister.termChanged(hale, renamed, decided(project))
                .forEach(deferral -> ok(project.deferrals().add(deferral)));

        assertThat(report(job(project, new ScriptedChatModel()).run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(openDeferrals(project))
                .singleElement()
                .extracting(Deferral::proposal)
                .isEqualTo("Гейл пішов.");
        assertThat(ok(tallies(project).codeWorkLeft(project.id()))).isFalse();
    }

    // The owner's rule: a rewrite the person undid is theirs to keep; the next Continue does not make it again.
    @Test
    void continue_afterUndoProofread_rewriteNotKeptAgain() {
        final TestProject project = book(QualityDial.BALANCED);
        final String russianLetter = "Дзвін ударыв 3 разів на світанку.";
        update(
                project,
                "Book.md:3",
                record -> record.withStatus(SegmentStatus.FLAGGED).withMachineTarget(russianLetter, russianLetter));
        final String rewrite = "Дзвін ударив 3 разів на світанку.";
        assertThat(report(job(project, rewriting(rewrite)).run()).end()).isEqualTo(JobState.COMPLETED);
        assertThat(stored(project, "Book.md:3").proofread())
                .extracting(ProofreadVerdict::outcome)
                .isEqualTo(ProofreadOutcome.KEPT);
        ok(actions(project).undoProofread(project.id(), "Book.md:3"));

        final ScriptedChatModel again = rewriting(rewrite);
        assertThat(report(job(project, again).run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(stored(project, "Book.md:3").machineTarget()).isEqualTo(russianLetter);
        assertThat(proofreadCalls(again)).isZero();
    }

    private TestProject book(final QualityDial dial) {
        final TestProject project = project(
                TestBooks.markdown(
                        tempDir.resolve("Book.md"),
                        "Hale went away.\n\n"
                                + lines(SIXTEEN - 1, line -> "The bell rang " + (line + 1) + " times at dawn.")),
                brief("en", "uk", dial));
        decideAll(project, SIXTEEN, line -> "Дзвін ударив " + line + " разів на світанку.");
        decide(project, "Book.md:0", HALE_LEFT);
        return project;
    }

    private static ScriptedChatModel rewriting(final String text) {
        return new ScriptedChatModel()
                .answerTo(
                        PROOFREAD,
                        Result.ok(new ChatResponse(
                                "{\"items\":[{\"id\":\"Book.md:3\",\"target\":\"" + text + "\"}]}",
                                FinishReason.STOP)));
    }

    private static SegmentActions actions(final TestProject project) {
        final RunStores stores = project.stores();
        return new SegmentActions(project.documents(), stores.openProjects(), stores.segments(), project.deferrals());
    }

    private static ProofreadTallies tallies(final TestProject project) {
        final RunStores stores = project.stores();
        return new ProofreadTallies(
                stores.openProjects(),
                stores.projects(),
                stores.segments(),
                stores.glossary(),
                stores.lexicon(),
                project.deferrals(),
                new DialParameterProfiles());
    }

    private static long proofreadCalls(final ScriptedChatModel model) {
        return model.requests().stream()
                .filter(request -> PROOFREAD.equals(ScriptedChatModel.callName(request)))
                .count();
    }
}
