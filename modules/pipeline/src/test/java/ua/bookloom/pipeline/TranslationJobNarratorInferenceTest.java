package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static ua.bookloom.pipeline.ChunkRunFixtures.DRAFT;
import static ua.bookloom.pipeline.ChunkRunFixtures.REVIEW;
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
import static ua.bookloom.pipeline.ChunkRunFixtures.reviewed;
import static ua.bookloom.pipeline.ChunkRunFixtures.target;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;
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
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.Narrator.GenderOrigin;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.revision.DeferralRegister;

/**
 * A first-person narrator whose gender nobody stated, seen through a whole Max run on the scripted model (15i.T5,
 * 15f.A4): once 20 first-person segments are decided and eight or more «я + past verb» forms of the narrator's own
 * words agree at 80 %, the gender is set as INFERRED and the segments holding the other gender's forms are revised
 * backwards; a book that does not reach that bar ends unknown, and a woman's book never ends male.
 */
class TranslationJobNarratorInferenceTest {

    private static final String SUMMARY = "summary";
    private static final String REVISION = "revision";
    private static final int GENEROUS = 60;
    private static final Narrator UNKNOWN_FIRST = new Narrator(NarratorPerson.FIRST, Gender.UNKNOWN);

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    private TestProject book(final List<String> sources, final QualityDial dial, final Narrator narrator) {
        final Path book = TestBooks.markdown(tempDir.resolve("Book.md"), NarratorInferenceBooks.book(sources));
        return TranslationJobTestSupport.project(book, brief("en", "uk", dial).withNarrator(narrator));
    }

    // Drafts in book order, a reviewer that finds nothing and a summary for as many calls as the run may make, then
    // the revisions a Max run asks for.
    private static ScriptedChatModel model(final List<String> drafts, final List<String> revisions) {
        final ScriptedChatModel model = new ScriptedChatModel();
        drafts.forEach(draft -> model.answerTo(DRAFT, target(draft)));
        IntStream.range(0, GENEROUS).forEach(index -> {
            model.answerTo(REVIEW, reviewed());
            model.answerTo(SUMMARY, summaryReply());
        });
        revisions.forEach(revision -> model.answerTo(REVISION, target(revision)));
        return model;
    }

    // Feminine renderings except at the given indexes, which are masculine.
    private static List<String> mostlyFeminine(final int count, final Set<Integer> masculine) {
        return IntStream.range(0, count)
                .mapToObj(index -> masculine.contains(index)
                        ? NarratorInferenceBooks.masculine(index)
                        : NarratorInferenceBooks.feminine(index))
                .toList();
    }

    private static Result<ChatResponse> summaryReply() {
        return Result.ok(new ChatResponse(
                "{\"summary\":{\"source\":\"The night went on.\",\"target\":\"Ніч тривала.\"},\"facts\":[]}",
                FinishReason.STOP));
    }

    private static Narrator narratorOf(final TestProject project) {
        return Objects.requireNonNull(
                        project.stores().projects().find(project.id()).data(), "project")
                .orElseThrow()
                .brief()
                .narrator();
    }

    private static String id(final int index) {
        return "Book.md:" + index;
    }

    private static List<Deferral> open(final TestProject project) {
        return Objects.requireNonNull(project.deferrals().open(project.id()).data(), "deferrals");
    }

    // The fixture itself: 24 clean masculine drafts are accepted as written and ask for no repair, no fix and no
    // revision, so every other test here differs from this one only by its slips.
    @Test
    void run_twentyFourMasculineFirstPersonDrafts_areAcceptedAsWrittenWithNoRevision() {
        final int count = 24;
        final TestProject project = book(NarratorInferenceBooks.firstSources(count), QualityDial.MAX, UNKNOWN_FIRST);
        final ScriptedChatModel model = model(NarratorInferenceBooks.firstTargets(count, Set.of()), List.of());

        report(job(project, model).run());

        assertThat(IntStream.range(0, count).boxed().toList()).allSatisfy(index -> {
            assertThat(stored(project, id(index)).status()).isEqualTo(SegmentStatus.ACCEPTED);
            assertThat(stored(project, id(index)).machineTarget()).isEqualTo(NarratorInferenceBooks.masculine(index));
        });
        assertThat(formats(model)).doesNotContain(REVISION);
    }

    // IF the majority were never counted, THEN a man's book with two feminine slips would stay unknown and keep them
    // (the Oct 9 runs: 26 of 41 wrong paragraphs came from one mistyped gender).
    @Test
    void run_maleFirstPersonBookWithTwoFeminineSlips_endsMaleInferredAndReRendersTheSlips() {
        final int count = 24;
        final TestProject project = book(NarratorInferenceBooks.firstSources(count), QualityDial.MAX, UNKNOWN_FIRST);
        final ScriptedChatModel model = model(
                NarratorInferenceBooks.firstTargets(count, Set.of(3, 12)),
                List.of(NarratorInferenceBooks.masculine(3), NarratorInferenceBooks.masculine(12)));

        assertThat(report(job(project, model).run()).end()).isEqualTo(JobState.COMPLETED);
        assertThat(narratorOf(project))
                .isEqualTo(new Narrator(NarratorPerson.FIRST, Gender.MALE, GenderOrigin.INFERRED, List.of()));
        assertThat(formats(model)).filteredOn(REVISION::equals).hasSize(2);
        assertThat(stored(project, id(3)).machineTarget()).isEqualTo(NarratorInferenceBooks.masculine(3));
        assertThat(stored(project, id(3)).status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(stored(project, id(12)).machineTarget()).isEqualTo(NarratorInferenceBooks.masculine(12));
        assertThat(stored(project, id(12)).status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(stored(project, id(0)).machineTarget()).isEqualTo(NarratorInferenceBooks.masculine(0));
        assertThat(open(project)).isEmpty();
    }

    // The Start question was answered "not stated" for a book the detector reads as first person: the narrator's
    // person is still unspecified, and the run's own count settles it.
    @Test
    void run_personUnspecifiedAndFirstPersonBook_endsFirstPersonMaleInferred() {
        final int count = 24;
        final TestProject project =
                book(NarratorInferenceBooks.firstSources(count), QualityDial.MAX, Narrator.unspecified());
        final ScriptedChatModel model = model(
                NarratorInferenceBooks.firstTargets(count, Set.of(3, 12)),
                List.of(NarratorInferenceBooks.masculine(3), NarratorInferenceBooks.masculine(12)));

        report(job(project, model).run());

        assertThat(narratorOf(project))
                .isEqualTo(new Narrator(NarratorPerson.FIRST, Gender.MALE, GenderOrigin.INFERRED, List.of()));
    }

    // Without the backwards stage (Balanced) the slips wait as gender deferrals on the narrator, for a later pass.
    @Test
    void run_balancedDial_leavesOneNarratorGenderDeferralPerSlipSegment() {
        final int count = 24;
        final TestProject project =
                book(NarratorInferenceBooks.firstSources(count), QualityDial.BALANCED, UNKNOWN_FIRST);
        final ScriptedChatModel model = model(NarratorInferenceBooks.firstTargets(count, Set.of(3, 12)), List.of());

        report(job(project, model).run());

        assertThat(narratorOf(project).gender()).isEqualTo(Gender.MALE);
        assertThat(open(project))
                .extracting(Deferral::segmentId, Deferral::reason, Deferral::waitingOn)
                .containsExactlyInAnyOrder(
                        tuple(id(3), DeferralReason.GENDER_UNKNOWN, DeferralRegister.NARRATOR),
                        tuple(id(12), DeferralReason.GENDER_UNKNOWN, DeferralRegister.NARRATOR));
        assertThat(stored(project, id(3)).machineTarget()).isEqualTo(NarratorInferenceBooks.feminine(3));
    }

    @Test
    void run_femaleFirstPersonBookWithTwoMasculineSlips_endsFemaleInferredAndReRendersTheSlips() {
        final int count = 24;
        final List<String> targets = mostlyFeminine(count, Set.of(5, 17));
        final TestProject project = book(NarratorInferenceBooks.firstSources(count), QualityDial.MAX, UNKNOWN_FIRST);
        final ScriptedChatModel model =
                model(targets, List.of(NarratorInferenceBooks.feminine(5), NarratorInferenceBooks.feminine(17)));

        report(job(project, model).run());

        assertThat(narratorOf(project))
                .isEqualTo(new Narrator(NarratorPerson.FIRST, Gender.FEMALE, GenderOrigin.INFERRED, List.of()));
        assertThat(stored(project, id(5)).machineTarget()).isEqualTo(NarratorInferenceBooks.feminine(5));
        assertThat(stored(project, id(17)).machineTarget()).isEqualTo(NarratorInferenceBooks.feminine(17));
    }

    // IF a bar below 80 % passed, THEN a woman's book with a third of its forms masculine could end with a male
    // narrator and have her own words "corrected" into the wrong gender.
    @Test
    void run_womansBookWithSeventyPercentFeminineForms_endsUnknownNeverMale() {
        final int count = 24;
        final List<String> targets = mostlyFeminine(count, Set.of(1, 4, 7, 10, 13, 16));
        final TestProject project = book(NarratorInferenceBooks.firstSources(count), QualityDial.MAX, UNKNOWN_FIRST);
        final ScriptedChatModel model = model(targets, List.of());

        report(job(project, model).run());

        assertThat(narratorOf(project).gender()).isNotEqualTo(Gender.MALE);
        assertThat(narratorOf(project)).isEqualTo(UNKNOWN_FIRST);
        assertThat(formats(model)).doesNotContain(REVISION);
        assertThat(open(project)).isEmpty();
    }

    // IF the bar counted segments of any person, THEN 15 «я» paragraphs among 30 would settle a gender the book's
    // other half never states.
    @Test
    void run_fifteenFirstPersonSegmentsAmongThirty_inferNothing() {
        final List<String> sources = new ArrayList<>();
        final List<String> targets = new ArrayList<>();
        IntStream.range(0, 15).forEach(index -> {
            sources.add(NarratorInferenceBooks.FIRST.get(index).source());
            targets.add(NarratorInferenceBooks.masculine(index));
            sources.add(NarratorInferenceBooks.THIRD.get(index).get(0));
            targets.add(NarratorInferenceBooks.THIRD.get(index).get(1));
        });
        final TestProject project = book(sources, QualityDial.MAX, UNKNOWN_FIRST);
        final ScriptedChatModel model = model(targets, List.of());

        report(job(project, model).run());

        assertThat(narratorOf(project)).isEqualTo(UNKNOWN_FIRST);
        assertThat(open(project)).isEmpty();
    }

    // The 20 are first-person segments, so a book of 24 where only the first 19 are first person has too few.
    @Test
    void run_nineteenFirstPersonSegmentsThenFiveThird_inferNothing() {
        final List<String> sources = new ArrayList<>(NarratorInferenceBooks.firstSources(19));
        final List<String> targets = new ArrayList<>(NarratorInferenceBooks.firstTargets(19, Set.of()));
        IntStream.range(0, 5).forEach(index -> {
            sources.add(NarratorInferenceBooks.THIRD.get(index).get(0));
            targets.add(NarratorInferenceBooks.THIRD.get(index).get(1));
        });
        final TestProject project = book(sources, QualityDial.MAX, UNKNOWN_FIRST);

        report(job(project, model(targets, List.of())).run());

        assertThat(narratorOf(project)).isEqualTo(UNKNOWN_FIRST);
    }

    // What the narrator is already known to be is never replaced by a count.
    @Test
    void run_narratorAlreadyVerified_isLeftAsItWas() {
        final int count = 24;
        final Narrator verified =
                new Narrator(NarratorPerson.FIRST, Gender.MALE, GenderOrigin.VERIFIED, List.of("Thank you, sir"));
        final TestProject project = book(NarratorInferenceBooks.firstSources(count), QualityDial.MAX, verified);
        final ScriptedChatModel model = model(NarratorInferenceBooks.firstTargets(count, Set.of()), List.of());

        report(job(project, model).run());

        assertThat(narratorOf(project)).isEqualTo(verified);
        assertThat(formats(model)).doesNotContain(REVISION);
    }

    // A suggestion is a guess nobody confirmed; twenty-two decided feminine forms are stronger evidence (the
    // orchestrator's
    // decision on 15i.T5), so the count replaces it.
    @Test
    void run_narratorOnlySuggestedMaleAndTheBookIsFeminine_endsFemaleInferred() {
        final int count = 24;
        final Narrator suggested = new Narrator(NarratorPerson.FIRST, Gender.MALE, GenderOrigin.SUGGESTED, List.of());
        final TestProject project = book(NarratorInferenceBooks.firstSources(count), QualityDial.BALANCED, suggested);
        final ScriptedChatModel model = model(mostlyFeminine(count, Set.of(5, 17)), List.of());

        report(job(project, model).run());

        assertThat(narratorOf(project))
                .isEqualTo(new Narrator(NarratorPerson.FIRST, Gender.FEMALE, GenderOrigin.INFERRED, List.of()));
        assertThat(open(project)).extracting(Deferral::segmentId).containsExactlyInAnyOrder(id(5), id(17));
    }

    // Without a decisive count the suggestion stays as it was.
    @Test
    void run_narratorOnlySuggestedAndNoDecisiveCount_staysSuggested() {
        final int count = 24;
        final Narrator suggested = new Narrator(NarratorPerson.FIRST, Gender.MALE, GenderOrigin.SUGGESTED, List.of());
        final List<String> targets = mostlyFeminine(count, Set.of(1, 4, 7, 10, 13, 16));
        final TestProject project = book(NarratorInferenceBooks.firstSources(count), QualityDial.BALANCED, suggested);

        report(job(project, model(targets, List.of())).run());

        assertThat(narratorOf(project)).isEqualTo(suggested);
    }

    @Test
    void run_thirdPersonNarrator_isNeverInferredOver() {
        final int count = 24;
        final Narrator third = new Narrator(NarratorPerson.THIRD, Gender.UNKNOWN);
        final TestProject project = book(NarratorInferenceBooks.firstSources(count), QualityDial.MAX, third);
        final ScriptedChatModel model = model(NarratorInferenceBooks.firstTargets(count, Set.of()), List.of());

        report(job(project, model).run());

        assertThat(narratorOf(project)).isEqualTo(third);
    }
}
