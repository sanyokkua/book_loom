package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.ChunkRunFixtures.S3;
import static ua.bookloom.pipeline.ChunkRunFixtures.T3;
import static ua.bookloom.pipeline.ChunkRunFixtures.target;
import static ua.bookloom.pipeline.ProofreadProjects.character;
import static ua.bookloom.pipeline.ProofreadProjects.decide;
import static ua.bookloom.pipeline.ProofreadProjects.decideAll;
import static ua.bookloom.pipeline.ProofreadProjects.decideWith;
import static ua.bookloom.pipeline.ProofreadProjects.decided;
import static ua.bookloom.pipeline.ProofreadProjects.lines;
import static ua.bookloom.pipeline.ProofreadProjects.locked;
import static ua.bookloom.pipeline.ProofreadProjects.marks;
import static ua.bookloom.pipeline.ProofreadProjects.ok;
import static ua.bookloom.pipeline.ProofreadProjects.openDeferrals;
import static ua.bookloom.pipeline.ProofreadProjects.recordUnknownGender;
import static ua.bookloom.pipeline.ProofreadProjects.update;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.Narrator.GenderOrigin;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.audit.AuditFindings;
import ua.bookloom.pipeline.proofread.DeferralRegister;
import ua.bookloom.pipeline.proofread.Mark;

/**
 * The Proofread stage's code part inside a whole run, at every dial: the audit closes Translate, then the term sweep,
 * the narrator count, the deferrals settled by code and the mark list, with no model call, and the run ends Completed
 * only after it.
 */
class ProofreadStageTest {

    private static final String HALE_LEFT = "Book.md:0";
    private static final String SAM_LEFT = "Book.md:1";
    private static final String WIND = "Book.md:2";
    private static final String STAGE_PROOFREAD = "stage PROOFREAD";
    private static final String FINISHED = "finished";

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // The audit closes Translate before the sweep; a sweep that writes a doubled word is not audited again afterwards.
    @Test
    void run_balancedLastChunk_ordersAuditSweepNarratorDeferralsList() {
        final TestProject project = bookWithDeferrals(QualityDial.BALANCED, "Гейл Гейл");
        final ScriptedChatModel model = balancedModel();
        final TranslationJobImpl translation = job(project, model);
        final List<String> events = described(translation);

        assertThat(report(translation.run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(events).containsSubsequence("decided " + WIND, STAGE_PROOFREAD, FINISHED);
        assertThat(stored(project, HALE_LEFT).machineTarget()).isEqualTo("Гейл Гейл пішов.");
        assertThat(stored(project, HALE_LEFT).status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(AuditFindings.checksOf(stored(project, HALE_LEFT).findings()))
                .as("the audit ran before the sweep and not after the stage")
                .isEmpty();
        assertThat(marks(project))
                .contains(new Mark(SAM_LEFT, Mark.Tier.MARKED, List.of("gender now known: Сем — female")));
        assertThat(ChunkRunFixtures.formats(model)).doesNotContain("revision");
    }

    // A deferral whose segment holds no word agreeing with the gender is settled with no call.
    @Test
    void deferral_futureVerb_resolvedNoCall() {
        final TestProject project = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), "Ivar will leave the key.\n\n" + S3),
                brief("en", "uk", QualityDial.BALANCED));
        final GlossaryEntry ivar =
                ok(project.stores().glossary().add(character(project, "Ivar", "Івар", Gender.UNKNOWN)));
        decide(project, "Book.md:0", "Ключ залишить Івар.");
        recordUnknownGender(project, "Book.md:0", List.of(ivar));
        ok(project.stores().glossary().update(ivar.withGender(Gender.FEMALE)));
        final ScriptedChatModel model = balancedModel();

        assertThat(report(job(project, model).run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(openDeferrals(project)).isEmpty();
        assertThat(model.requests()).as("the draft only").hasSize(1);
    }

    // At Fast the stage makes no model call and leaves six segments for the desk, each with its reasons.
    @Test
    void run_fast_noModelCallAndDeskListsSix() {
        final TestProject project = sixMarkBook();
        final ScriptedChatModel model = new ScriptedChatModel().answer(target(T3));

        assertThat(report(job(project, model).run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(model.requests())
                .as("only the draft of the one pending segment")
                .hasSize(1);
        assertThat(marks(project))
                .extracting(Mark::segmentId, Mark::tier)
                .containsExactly(
                        tuple("Book.md:0", Mark.Tier.MARKED),
                        tuple("Book.md:1", Mark.Tier.MARKED),
                        tuple("Book.md:2", Mark.Tier.DOUBTFUL),
                        tuple("Book.md:3", Mark.Tier.DOUBTFUL),
                        tuple("Book.md:4", Mark.Tier.DOUBTFUL),
                        tuple("Book.md:5", Mark.Tier.MARKED));
        assertThat(marks(project).get(2).reasons()).containsExactly("recurring term off its rendering: door → двері");
        assertThat(marks(project).get(3).reasons()).containsExactly("name form missing: Ivar → Івар");
    }

    // Continue at Fast runs the code part again: a gender set after the run is settled or left as a mark.
    @Test
    void continue_fastAfterGenderSetAfterRun_runsCodeOnlyProofreadAgain() {
        final TestProject project = project(
                TestBooks.markdown(
                        tempDir.resolve("Book.md"), "Ivar said he would return.\n\nIvar will leave the key."),
                brief("en", "uk", QualityDial.FAST));
        final GlossaryEntry ivar = ok(project.stores().glossary().add(character(project, "Ivar", "Івар", Gender.MALE)));
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(target("Івар сказав, що повернеться."))
                .answer(target("Ключ залишить Івар."));
        assertThat(report(job(project, model).run()).end()).isEqualTo(JobState.COMPLETED);
        final GlossaryEntry female = ivar.withGender(Gender.FEMALE);
        ok(project.stores().glossary().update(female));
        DeferralRegister.changed(ivar, female, decided(project), Set.of("Book.md:0", "Book.md:1"), "uk")
                .forEach(deferral -> ok(project.deferrals().add(deferral)));
        final List<String> events = new CopyOnWriteArrayList<>();
        final TranslationJobImpl again = job(project, model);
        again.subscribe(event -> events.add(describe(event)));

        assertThat(report(again.run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(model.requests())
                .as("the two drafts of the first run, no call on Continue")
                .hasSize(2);
        assertThat(events).containsSubsequence(STAGE_PROOFREAD, FINISHED);
        assertThat(openDeferrals(project)).extracting(Deferral::segmentId).containsExactly("Book.md:0");
        assertThat(marks(project))
                .containsExactly(new Mark("Book.md:0", Mark.Tier.MARKED, List.of("gender now known: Івар — female")));
    }

    // The narrator is inferred before the deferrals are settled, so a deferral waiting on the narrator settles in the
    // same run: one that agrees is resolved, the minority one stays as a mark.
    @Test
    void narrator_eightFemalePastFormsInTargets_storedInferredBeforeDeferrals() {
        final BookBrief brief =
                brief("en", "uk", QualityDial.FAST).withNarrator(new Narrator(NarratorPerson.FIRST, Gender.UNKNOWN));
        final TestProject project = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), lines(21, line -> "I went out, line " + line + ".")),
                brief);
        decideAll(project, 21, line -> line == 20 ? "Я вийшов, рядок 20." : "Я вийшла, рядок " + line + ".");
        DeferralRegister.narratorGender(project.id(), List.of("Book.md:3"))
                .forEach(deferral -> ok(project.deferrals().add(deferral)));

        assertThat(report(job(project, new ScriptedChatModel()).run()).end()).isEqualTo(JobState.COMPLETED);

        final Narrator narrator = ok(project.stores().projects().find(project.id()))
                .orElseThrow()
                .brief()
                .narrator();
        assertThat(narrator.gender()).isEqualTo(Gender.FEMALE);
        assertThat(narrator.genderOrigin()).isEqualTo(GenderOrigin.INFERRED);
        assertThat(openDeferrals(project)).extracting(Deferral::segmentId).containsExactly("Book.md:20");
        assertThat(marks(project))
                .filteredOn(mark -> mark.segmentId().equals("Book.md:20"))
                .singleElement()
                .satisfies(mark -> assertThat(mark.reasons()).contains("gender now known: narrator — female"));
    }

    // The three stages are announced in order, and the run finishes Completed only after Proofread.
    @Test
    void run_fastBookTxt_stagesPrepareTranslateProofreadThenCompleted() {
        final TestProject project =
                project(TestBooks.txt(tempDir.resolve("Book.txt"), "One.\n\nTwo.\n\nThree."), brief("en", "uk"));
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(target("Один."))
                .answer(target("Два."))
                .answer(target("Три."));
        final TranslationJobImpl translation = job(project, model);
        final List<String> events = described(translation);

        final JobReport report = report(translation.run());

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(events.stream().filter(event -> event.startsWith("stage ")).toList())
                .containsExactly("stage PREP", "stage TRANSLATE", STAGE_PROOFREAD);
        assertThat(events.getLast()).isEqualTo(FINISHED);
    }

    // A stop asked for as Proofread starts ends the run Cancelled; what Translate decided stays decided.
    @Test
    void stop_duringProofread_cancelledWithDecidedSegmentsKept() {
        final TestProject project = bookWithDeferrals(QualityDial.BALANCED, "Гейл");
        final TranslationJobImpl translation = job(project, balancedModel());
        translation.subscribe(event -> stopOnProofread(translation, event));

        assertThat(report(translation.run()).end()).isEqualTo(JobState.CANCELLED);

        assertThat(stored(project, WIND).status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(stored(project, WIND).machineTarget()).isEqualTo(T3);
        assertThat(stored(project, HALE_LEFT).machineTarget()).as("not swept").isEqualTo("Хейл пішов.");
    }

    // With no model call at Fast the deterministic sweep still runs and a gender deferral the text contradicts waits.
    @Test
    void run_fastWithRenamedTermAndGenderNowKnown_sweepsTermAndKeepsGenderOpen() {
        final TestProject project = bookWithDeferrals(QualityDial.FAST, "Гейл");
        final ScriptedChatModel model = new ScriptedChatModel().answer(target(T3));

        assertThat(report(job(project, model).run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(stored(project, HALE_LEFT).machineTarget()).isEqualTo("Гейл пішов.");
        assertThat(stored(project, SAM_LEFT).machineTarget()).isEqualTo("Сем пішов.");
        assertThat(model.requests()).as("the one draft only").hasSize(1);
        assertThat(openDeferrals(project))
                .extracting(Deferral::segmentId, Deferral::reason)
                .containsExactly(tuple(SAM_LEFT, DeferralReason.GENDER_UNKNOWN));
    }

    // IF a suggested gender were acted on, THEN one wrong guess would send every paragraph that waits on the character
    // to the model; only a gender the book verified or the person set marks a segment.
    @Test
    void run_balancedGenderOnlySuggested_noProofreadCallAndTheDeferralWaits() {
        final TestProject project = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), "Sam went away.\n\n" + S3),
                brief("en", "uk", QualityDial.BALANCED));
        final GlossaryEntry sam = ok(project.stores().glossary().add(character(project, "Sam", "Сем", Gender.UNKNOWN)));
        decide(project, "Book.md:0", "Сем пішов.");
        recordUnknownGender(project, "Book.md:0", List.of(sam));
        ok(project.stores().glossary().update(sam.withSuggestedGender(Gender.FEMALE)));
        final ScriptedChatModel model = balancedModel();

        assertThat(report(job(project, model).run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(model.requests()).as("the draft only").hasSize(1);
        assertThat(openDeferrals(project)).hasSize(1);
        assertThat(marks(project)).isEmpty();
    }

    // IF matter were sent to the model, THEN a contents page and a copyright line would cost a call each.
    @Test
    void run_balancedFlaggedMatterAndBody_sendsOnlyTheBodyParagraphs() {
        final TestProject project = project(
                TestBooks.epubWithFrontMatterAndNavigation(
                        tempDir.resolve("Book.epub"),
                        List.of("All rights reserved."),
                        List.of(List.of("First line.", "Second line.")),
                        List.of("Label one", "Label two")),
                brief("en", "uk", QualityDial.BALANCED));
        ok(project.stores().segments().all(project.id())).forEach(record -> {
            decide(project, record.segmentId(), "Текст.");
            update(project, record.segmentId(), flagged -> flagged.withStatus(SegmentStatus.FLAGGED));
        });
        final ScriptedChatModel model = new ScriptedChatModel();

        assertThat(report(job(project, model).run()).end()).isEqualTo(JobState.COMPLETED);

        final String sent = model.requests().stream()
                .filter(request -> "proofread".equals(ScriptedChatModel.callName(request)))
                .flatMap(request -> request.messages().stream())
                .map(ChatMessage::content)
                .collect(Collectors.joining("\n"));
        assertThat(sent).contains("ch01.xhtml:0", "ch01.xhtml:1").doesNotContain("copyright.xhtml", "aux:");
    }

    /**
     * A three-paragraph book whose first two are decided before the run: the first used {@code Хейл}, since renamed
     * {@code renamed} and locked, and the second names {@code Sam}, whose gender was unknown then and is female now.
     */
    private TestProject bookWithDeferrals(final QualityDial dial, final String renamed) {
        final TestProject project = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), "Hale went away.\n\nSam went away.\n\n" + S3),
                brief("en", "uk", dial));
        final GlossaryEntry hale = locked(project, "Hale", "Хейл");
        final GlossaryEntry sam = character(project, "Sam", "Сем", Gender.UNKNOWN);
        ok(project.stores().glossary().add(hale));
        ok(project.stores().glossary().add(sam));
        decide(project, HALE_LEFT, "Хейл пішов.");
        decide(project, SAM_LEFT, "Сем пішов.");
        recordUnknownGender(project, SAM_LEFT, List.of(sam));
        final GlossaryEntry changed = locked(project, "Hale", renamed);
        ok(project.stores().glossary().update(changed));
        DeferralRegister.termChanged(hale, changed, decided(project))
                .forEach(deferral -> ok(project.deferrals().add(deferral)));
        ok(project.stores().glossary().update(character(project, "Sam", "Сем", Gender.FEMALE)));
        return project;
    }

    /**
     * Seven paragraphs, six decided before the run so that each gives the list one entry: flagged, a gender now known
     * that the text contradicts, a recurring term written otherwise, a map name missing, a note, a repair-level
     * finding; the seventh is drafted clean.
     */
    private TestProject sixMarkBook() {
        final TestProject project = project(
                TestBooks.markdown(
                        tempDir.resolve("Book.md"),
                        String.join(
                                "\n\n",
                                "Hale went away.",
                                "Sam went away.",
                                "The door creaked.",
                                "Ivar came in.",
                                "The boats rested.",
                                "The wind rose.",
                                S3)),
                brief("en", "uk", QualityDial.FAST));
        final GlossaryEntry sam = ok(project.stores().glossary().add(character(project, "Sam", "Сем", Gender.UNKNOWN)));
        ok(project.stores().glossary().add(character(project, "Ivar", "Івар", Gender.MALE)));
        ok(project.stores().lexicon().put(LexiconEntry.of(project.id(), "door").withChosen("двері")));
        decideSix(project);
        recordUnknownGender(project, SAM_LEFT, List.of(sam));
        ok(project.stores().glossary().update(sam.withGender(Gender.FEMALE)));
        return project;
    }

    private static void decideSix(final TestProject project) {
        update(
                project,
                "Book.md:0",
                record -> record.withStatus(SegmentStatus.FLAGGED).withMachineTarget("Хейл пішов.", "Хейл пішов."));
        decide(project, SAM_LEFT, "Сем пішов.");
        decide(project, "Book.md:2", "Брама скрипнула.");
        decide(project, "Book.md:3", "Він увійшов.");
        decideWith(
                project,
                "Book.md:4",
                "Човни відпочивали.",
                new QaFinding("style", Severity.LOW, "A note.", "reviewer"));
        decideWith(
                project,
                "Book.md:5",
                "Вітер здійнявся.",
                new QaFinding("gender", Severity.MEDIUM, "A word to check.", "name-gender"));
    }

    private static ScriptedChatModel balancedModel() {
        return new ScriptedChatModel().answer(target(T3));
    }

    private static void stopOnProofread(final TranslationJobImpl translation, final JobEvent event) {
        if (event instanceof StageStarted started && started.stage() == JobStage.PROOFREAD) {
            translation.cancel();
        }
    }

    private static List<String> described(final TranslationJobImpl translation) {
        final List<String> events = new CopyOnWriteArrayList<>();
        translation.subscribe(event -> events.add(describe(event)));
        return events;
    }

    private static String describe(final JobEvent event) {
        return switch (event) {
            case SegmentDecided decided -> "decided " + decided.segmentId();
            case StageStarted started -> "stage " + started.stage();
            case Finished _ -> FINISHED;
            default -> event.getClass().getSimpleName();
        };
    }
}
