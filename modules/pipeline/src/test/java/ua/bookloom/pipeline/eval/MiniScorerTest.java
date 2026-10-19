package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.eval.MiniExpectations.CheckKind;
import ua.bookloom.pipeline.eval.MiniExpectations.Expectation;
import ua.bookloom.pipeline.eval.MiniExpectations.GlossaryExpectation;
import ua.bookloom.pipeline.eval.MiniRun.Calls;
import ua.bookloom.pipeline.eval.MiniRun.Outcome;

/** Each check of the mini suite can fail as well as pass: the scorer proven on hand-written runs. */
class MiniScorerTest {

    private static Outcome outcome(final String id, final String source, final String target) {
        return new Outcome(id, source, target, SegmentStatus.ACCEPTED, List.of());
    }

    private static MiniRun run(
            final NamePolicy policy, final List<Outcome> outcomes, final List<GlossaryEntry> glossary) {
        return new MiniRun(
                policy,
                JobState.COMPLETED,
                outcomes.stream().collect(Collectors.toMap(Outcome::id, o -> o)),
                glossary,
                List.of(),
                new Calls(1, 1, 1),
                Duration.ofSeconds(1));
    }

    private static MiniResult scoreOne(final CheckKind check, final List<String> args, final Outcome... outcomes) {
        final String first = outcomes[0].id();
        final Expectation expectation = new Expectation("x", "d", first, List.of(), check, args, List.of());
        return MiniScorer.score(
                        new MiniExpectations(List.of(), List.of(expectation), List.of()),
                        run(NamePolicy.TRANSLITERATE, List.of(outcomes), List.of()))
                .get(0);
    }

    private static MiniResult scoreGlossary(
            final GlossaryExpectation expectation, final NamePolicy policy, final GlossaryEntry... entries) {
        return MiniScorer.score(
                        new MiniExpectations(List.of(), List.of(), List.of(expectation)),
                        run(policy, List.of(), List.of(entries)))
                .get(0);
    }

    private static GlossaryEntry entry(
            final String term, final String target, final TermType type, final Gender gender) {
        return new GlossaryEntry("id-" + term, "p", term, target, type, gender, false);
    }

    @Test
    void score_latinWordLeft_failsNoLatinExceptAndNamesIt() {
        final MiniResult result =
                scoreOne(CheckKind.NO_LATIN_EXCEPT, List.of("KX-7"), outcome("m1", "a", "Насос KX-7 і Velmora"));

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("Velmora");
    }

    @Test
    void score_onlyTheAllowedCodeIsLatin_passesNoLatinExcept() {
        assertThat(scoreOne(CheckKind.NO_LATIN_EXCEPT, List.of("KX-7"), outcome("m1", "a", "Табличка KX-7 ще блищала."))
                        .passed())
                .isTrue();
    }

    @Test
    void score_russianLetterInTarget_failsNoRussianLetters() {
        assertThat(scoreOne(CheckKind.NO_RUSSIAN_LETTERS, List.of(), outcome("m1", "a", "Підзываючи його"))
                        .passed())
                .isFalse();
        assertThat(scoreOne(CheckKind.NO_RUSSIAN_LETTERS, List.of(), outcome("m1", "a", "Підзиваючи його"))
                        .passed())
                .isTrue();
    }

    @Test
    void score_flaggedSegment_failsNotFlagged() {
        final Outcome flagged = new Outcome("m1", "a", "текст", SegmentStatus.FLAGGED, List.of());

        assertThat(scoreOne(CheckKind.NOT_FLAGGED, List.of(), flagged).passed()).isFalse();
        assertThat(scoreOne(CheckKind.NOT_FLAGGED, List.of(), outcome("m1", "a", "текст"))
                        .passed())
                .isTrue();
    }

    @Test
    void score_storedSentenceCountFinding_failsNoFinding() {
        final Outcome withFinding = new Outcome(
                "m1",
                "a",
                "текст",
                SegmentStatus.ACCEPTED,
                List.of(new QaFinding("omission", Severity.LOW, "one sentence short", "sentence-count")));

        assertThat(scoreOne(CheckKind.NO_FINDING, List.of("sentence-count"), withFinding)
                        .passed())
                .isFalse();
        assertThat(scoreOne(CheckKind.NO_FINDING, List.of("name-swap"), withFinding)
                        .passed())
                .isTrue();
    }

    @Test
    void score_feminineFormAfterYa_failsNarratorMale() {
        assertThat(scoreOne(CheckKind.NARRATOR_MALE, List.of(), outcome("m1", "a", "Я приїхала на поромі."))
                        .passed())
                .isFalse();
        assertThat(scoreOne(CheckKind.NARRATOR_MALE, List.of(), outcome("m1", "a", "Я приїхав на поромі."))
                        .passed())
                .isTrue();
    }

    @Test
    void score_aOneWordReplyToALongParagraph_failsMinRatio() {
        final String source = "The harbour office had been built in the year of the great frost.";

        assertThat(scoreOne(CheckKind.MIN_RATIO, List.of("0.6"), outcome("m1", source, "Ананас"))
                        .passed())
                .isFalse();
        assertThat(scoreOne(
                                CheckKind.MIN_RATIO,
                                List.of("0.6"),
                                outcome("m1", source, "Контору гавані збудували за великого морозу."))
                        .passed())
                .isTrue();
    }

    private static MiniResult scoreAddress(final String first, final String second) {
        final Expectation group =
                new Expectation("x", "d", null, List.of("m1", "m2"), CheckKind.ADDRESS_SAME, List.of(), List.of());
        return MiniScorer.score(
                        new MiniExpectations(List.of(), List.of(group), List.of()),
                        run(
                                NamePolicy.TRANSLITERATE,
                                List.of(outcome("m1", "a", first), outcome("m2", "b", second)),
                                List.of()))
                .get(0);
    }

    @Test
    void score_tiAndViTogetherAcrossAGroup_failsAddressSame() {
        final MiniResult mixed = scoreAddress("Ти не повинен тут бути.", "Ви маєте моє слово.");
        final MiniResult formal = scoreAddress("Ви не повинні тут бути.", "Ви маєте моє слово.");

        assertThat(mixed.passed()).isFalse();
        assertThat(formal.passed()).isTrue();
    }

    @Test
    void score_matchesAndAbsent_passOnlyWhenThePatternIsThereOrNot() {
        final Outcome target = outcome("m1", "a", "Насос KX-7");

        assertThat(scoreOne(CheckKind.MATCHES, List.of("KX-7"), target).passed())
                .isTrue();
        assertThat(scoreOne(CheckKind.MATCHES, List.of("Velmora"), target).passed())
                .isFalse();
        assertThat(scoreOne(CheckKind.ABSENT, List.of("^\\W*[\\p{L}]{1,14}\\W*$"), outcome("m1", "a", "Ананас"))
                        .passed())
                .isFalse();
        assertThat(scoreOne(CheckKind.ABSENT, List.of("Velmora"), target).passed())
                .isTrue();
    }

    @Test
    void score_segmentTheRunNeverDecided_failsNamingIt() {
        final MiniResult result = scoreOne(CheckKind.NOT_FLAGGED, List.of(), outcome("m9", "a", "текст"));
        final Expectation missing =
                new Expectation("x", "d", "m1", List.of(), CheckKind.NOT_FLAGGED, List.of(), List.of());

        final MiniResult absent = MiniScorer.score(
                        new MiniExpectations(List.of(), List.of(missing), List.of()),
                        run(NamePolicy.TRANSLITERATE, List.of(outcome("m9", "a", "текст")), List.of()))
                .get(0);

        assertThat(result.passed()).isTrue();
        assertThat(absent.passed()).isFalse();
        assertThat(absent.detail()).contains("m1");
    }

    @Test
    void score_expectationOfAnotherNamePolicy_isLeftOutNotPassed() {
        final Expectation keepOnly = new Expectation(
                "x", "d", "m1", List.of(), CheckKind.MATCHES, List.of("Velmora"), List.of(NamePolicy.KEEP_ORIGINAL));
        final MiniExpectations expectations = new MiniExpectations(List.of(), List.of(keepOnly), List.of());
        final List<Outcome> outcomes = List.of(outcome("m1", "a", "Годинник Вельмора"));

        assertThat(MiniScorer.score(expectations, run(NamePolicy.TRANSLITERATE, outcomes, List.of())))
                .isEmpty();
        assertThat(MiniScorer.score(expectations, run(NamePolicy.KEEP_ORIGINAL, outcomes, List.of())))
                .extracting(MiniResult::passed)
                .containsExactly(false);
    }

    @Test
    void score_everyBlockCheck_namesTheBlocksThatFail() {
        final Expectation every =
                new Expectation("every", "d", null, List.of(), CheckKind.NO_RUSSIAN_LETTERS, List.of(), List.of());

        final MiniResult result = MiniScorer.score(
                        new MiniExpectations(List.of(every), List.of(), List.of()),
                        run(
                                NamePolicy.TRANSLITERATE,
                                List.of(outcome("m1", "a", "добре"), outcome("m2", "b", "сынок")),
                                List.of()))
                .get(0);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).isEqualTo("failing: m2");
    }

    @Test
    void score_glossaryGender_isSettledOnlyWhenNotMerelySuggested() {
        final GlossaryExpectation settled = new GlossaryExpectation(
                "g",
                "d",
                List.of("Ilsabet"),
                false,
                false,
                TermType.CHARACTER,
                null,
                Gender.FEMALE,
                true,
                null,
                false,
                false,
                null,
                0,
                List.of());
        final GlossaryEntry suggested =
                entry("Ilsabet", "Ільзабет", TermType.CHARACTER, Gender.UNKNOWN).withSuggestedGender(Gender.FEMALE);
        final GlossaryEntry chosen =
                entry("Ilsabet", "Ільзабет", TermType.CHARACTER, Gender.UNKNOWN).withGender(Gender.FEMALE);

        assertThat(scoreGlossary(settled, NamePolicy.TRANSLITERATE, suggested).passed())
                .isFalse();
        assertThat(scoreGlossary(settled, NamePolicy.TRANSLITERATE, chosen).passed())
                .isTrue();
    }

    private static GlossaryExpectation keptExpectation() {
        return new GlossaryExpectation(
                "g",
                "d",
                List.of("Velmora"),
                false,
                false,
                null,
                null,
                null,
                null,
                true,
                false,
                true,
                null,
                0,
                List.of());
    }

    @Test
    void score_brandKeptUnderKeepOriginal_needsTheLockedSourceSpelling() {
        final GlossaryExpectation kept = keptExpectation();

        assertThat(scoreGlossary(
                                kept,
                                NamePolicy.KEEP_ORIGINAL,
                                entry("Velmora", "Velmora", TermType.TERM, Gender.UNKNOWN)
                                        .withLocked(true))
                        .passed())
                .isTrue();
        assertThat(scoreGlossary(
                                kept,
                                NamePolicy.KEEP_ORIGINAL,
                                entry("Velmora", "Вельмора", TermType.TERM, Gender.UNKNOWN))
                        .passed())
                .isFalse();
    }

    private static GlossaryExpectation placeExpectation() {
        return new GlossaryExpectation(
                "g",
                "d",
                List.of("Brindle Harbour"),
                false,
                false,
                TermType.PLACE,
                null,
                null,
                null,
                null,
                true,
                false,
                "^Бр",
                2,
                List.of());
    }

    private static GlossaryExpectation junkExpectation() {
        return new GlossaryExpectation(
                "j",
                "d",
                List.of("Nobody"),
                true,
                false,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                null,
                0,
                List.of());
    }

    private static boolean placeScores(final String target) {
        return scoreGlossary(
                        placeExpectation(),
                        NamePolicy.TRANSLITERATE,
                        entry("Brindle Harbour", target, TermType.PLACE, Gender.UNKNOWN))
                .passed();
    }

    @Test
    void score_multiWordPlaceTarget_isCheckedWordByWord() {
        assertThat(placeScores("Брендл Гавань")).isTrue();
        assertThat(placeScores("Брендл")).isFalse();
        assertThat(scoreGlossary(placeExpectation(), NamePolicy.TRANSLITERATE).passed())
                .isFalse();
    }

    @Test
    void score_junkEntry_failsOnlyWhenTheGlossaryHoldsIt() {
        final GlossaryEntry junk = entry("Nobody", "Ніхто", TermType.CHARACTER, Gender.UNKNOWN);

        assertThat(scoreGlossary(junkExpectation(), NamePolicy.TRANSLITERATE, junk)
                        .passed())
                .isFalse();
        assertThat(scoreGlossary(junkExpectation(), NamePolicy.TRANSLITERATE).passed())
                .isTrue();
    }
}
