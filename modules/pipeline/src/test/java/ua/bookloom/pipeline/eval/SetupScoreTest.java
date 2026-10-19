package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.pipeline.eval.SetupScore.Observed;
import ua.bookloom.pipeline.eval.StageCases.SetupCase;

/**
 * The setup suite's per-field score (15i.E3) over a scripted model, with hard-coded counts: genre, register, narrator
 * and narrator gender against the gold of each case, and the knowledge answer of the production verdict split
 * into the invented books, which must be "unknown", and the classics, which must be "known".
 */
class SetupScoreTest {

    private static final Map<String, String> KNOWLEDGE_SLIPS =
            Map.of("invented-02-small-clocks", "known", "classic-dracula", "unknown");

    @TempDir
    private Path dir;

    private static Observed asked(final SetupCase setupCase) {
        return new Observed(setupCase, null, KNOWLEDGE_SLIPS.getOrDefault(setupCase.id(), setupCase.expectKnowledge()));
    }

    // IF the scripted model flips two of the fifteen cases, THEN exactly those two briefs miss, and the report says
    // 13 of 15 for the register, 15 of 15 for the genre and the narrator's gender, and 14 of 15 for the narrator: since
    // 15i.B4 the detector decides the narrator of the one flipped book long enough to count, whatever its windows say.
    // The same two
    // are also answered the wrong way about what the model knows: the invented weir is "known" (echoed back), the
    // classic whale is "unknown"; the untitled book makes no call and is right.
    @Test
    void score_scriptedModelWrongOnTwoCases_countsEachBriefFieldAgainstTheGold() {
        final List<SetupCase> cases = StageCases.setup();
        final SetupEvalRunner runner = new SetupEvalRunner(
                new SetupScriptedModel(cases, Set.of("classic-moby-dick", "invented-06-thirteenth-weir")), dir);

        final List<StageRow> rows = runner.run(cases);
        final SetupScore score = runner.score();

        assertThat(rows.stream().filter(row -> row.id().endsWith(":brief") && !row.passed()))
                .as(rows.toString())
                .extracting(StageRow::id)
                .containsExactly("invented-06-thirteenth-weir:brief", "classic-moby-dick:brief");
        assertThat(score.fields())
                .extracting(SetupScore.Field::name, SetupScore.Field::right, SetupScore.Field::total)
                .containsExactly(
                        tuple("genre", 15, 15),
                        tuple("register", 13, 15),
                        tuple("narrator", 14, 15),
                        tuple("narratorGender", 15, 15),
                        tuple("knowledge.invented", 10, 11),
                        tuple("knowledge.classic", 3, 4));
        assertThat(rows.stream().filter(row -> row.id().endsWith(":knowledge") && !row.passed()))
                .as(rows.toString())
                .extracting(StageRow::id)
                .containsExactly("invented-06-thirteenth-weir:knowledge", "classic-moby-dick:knowledge");
        assertThat(score.line())
                .isEqualTo("genre 15/15 register 13/15 narrator 14/15 narratorGender 15/15"
                        + " knowledge.invented 10/11 knowledge.classic 3/4");
    }

    @Test
    void observed_runOverAllCases_holdsOneEntryPerCaseInOrderWithItsKnowledgeAnswer() {
        final List<SetupCase> cases = StageCases.setup();
        final SetupEvalRunner runner = new SetupEvalRunner(new SetupScriptedModel(cases, Set.of()), dir);

        runner.run(cases);

        assertThat(runner.observed()).hasSize(15);
        assertThat(runner.observed())
                .extracting(observed -> observed.setupCase().id())
                .startsWith("setup-en-uk-first-person", "setup-ru-uk-third-person", "invented-01-lantern-ledger");
        assertThat(runner.observed())
                .allSatisfy(observed -> assertThat(observed.brief()).isNotNull());
        assertThat(runner.observed())
                .extracting(Observed::knowledge)
                .containsExactly(
                        "unknown", "unknown", "unknown", "unknown", "unknown", "unknown", "unknown", "unknown",
                        "unknown", "unknown", "known", "known", "known", "known", "unknown");
        assertThat(runner.score().line()).endsWith("knowledge.invented 11/11 knowledge.classic 4/4");
    }

    // IF the report mixed the invented books with the classics, THEN a model that knows the classics and invents an
    // answer for one made-up book would look better than it is: the two groups are scored apart.
    @Test
    void score_knowledgeAnswersWithOneInventedBookClaimedKnownAndOneClassicMissed_splitsTheGroups() {
        final List<Observed> observed =
                StageCases.setup().stream().map(SetupScoreTest::asked).toList();

        final SetupScore score = SetupScore.of(observed);

        assertThat(score.field("knowledge.invented")).isEqualTo(new SetupScore.Field("knowledge.invented", 10, 11));
        assertThat(score.field("knowledge.classic")).isEqualTo(new SetupScore.Field("knowledge.classic", 3, 4));
        assertThat(score.field("knowledge.invented").share()).isCloseTo(0.909, within(0.001));
        assertThat(score.line()).endsWith("knowledge.invented 10/11 knowledge.classic 3/4");
    }

    @Test
    void score_failedBrief_countsWrongInEveryBriefFieldAndLeavesKnowledgeNotAsked() {
        final List<Observed> observed = StageCases.setup().stream()
                .map(setupCase -> new Observed(setupCase, null, null))
                .toList();

        final SetupScore score = SetupScore.of(observed);

        assertThat(score.fields())
                .extracting(SetupScore.Field::name, SetupScore.Field::right, SetupScore.Field::total)
                .containsExactly(
                        tuple("genre", 0, 15),
                        tuple("register", 0, 15),
                        tuple("narrator", 0, 15),
                        tuple("narratorGender", 0, 15),
                        tuple("knowledge.invented", 0, 0),
                        tuple("knowledge.classic", 0, 0));
    }

    // The gold of the first invented case: fantasy, neutral, third person, gender unknown.
    @Test
    void score_briefMatchingTheGoldOfOneCase_isOneOfOneInEachBriefField() {
        final SetupCase ledger = StageCases.setup().get(2);
        final BriefSuggestion brief =
                new BriefSuggestion("Dark fantasy", Register.NEUTRAL, null, null, NarratorPerson.THIRD, Gender.UNKNOWN);

        final SetupScore score = SetupScore.of(List.of(new Observed(ledger, brief, "unknown")));

        assertThat(score.line())
                .isEqualTo("genre 1/1 register 1/1 narrator 1/1 narratorGender 1/1"
                        + " knowledge.invented 1/1 knowledge.classic n/a");
    }

    @Test
    void score_briefWithAGenreOutsideTheClassAndAGender_missesThoseTwoFields() {
        final SetupCase ledger = StageCases.setup().get(2);
        final BriefSuggestion brief =
                new BriefSuggestion("western", Register.NEUTRAL, null, null, NarratorPerson.THIRD, Gender.FEMALE);

        final SetupScore score = SetupScore.of(List.of(new Observed(ledger, brief, null)));

        assertThat(score.line())
                .isEqualTo("genre 0/1 register 1/1 narrator 1/1 narratorGender 0/1"
                        + " knowledge.invented n/a knowledge.classic n/a");
    }
}
