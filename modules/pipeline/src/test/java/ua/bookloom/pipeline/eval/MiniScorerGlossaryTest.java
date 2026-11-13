package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.eval.MiniExpectations.CheckKind;
import ua.bookloom.pipeline.eval.MiniExpectations.Expectation;
import ua.bookloom.pipeline.eval.MiniExpectations.GlossaryExpectation;

/** The glossary-level graders of the mini suite, proven on hand-written runs. */
class MiniScorerGlossaryTest {

    @ParameterizedTest(name = "{0} {1} -> {3}")
    @CsvSource(
            delimiter = '|',
            value = {
                "GLOSSARY_ONE_ENTRY|Велемир;Велемира|Велемир|true",
                "GLOSSARY_ONE_ENTRY|Велемир;Велемира|Велемир;Велемира|false",
                "GLOSSARY_GENDER|Велемир;Велемира;MALE|Велемир|true",
                "GLOSSARY_GENDER|Велемир;Велемира;FEMALE|Велемир|false"
            })
    void score_glossaryGrader_readsTheStoredEntriesOnly(
            final CheckKind check, final String args, final String terms, final boolean passes) {
        final List<GlossaryEntry> entries = Stream.of(terms.split(";"))
                .map(term -> MiniScorerTest.entry(term, "Велемир", TermType.CHARACTER, Gender.MALE))
                .toList();
        final Expectation expectation =
                new Expectation("x", "d", null, List.of(), check, List.of(args.split(";")), List.of());

        final MiniResult result = MiniScorer.score(
                        new MiniExpectations(List.of(), List.of(expectation), List.of()),
                        MiniScorerTest.run(NamePolicy.TRANSLITERATE, List.of(), entries))
                .get(0);

        assertThat(result.passed()).isEqualTo(passes);
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
        final GlossaryEntry suggested = MiniScorerTest.entry("Ilsabet", "Ільзабет", TermType.CHARACTER, Gender.UNKNOWN)
                .withSuggestedGender(Gender.FEMALE);
        final GlossaryEntry chosen = MiniScorerTest.entry("Ilsabet", "Ільзабет", TermType.CHARACTER, Gender.UNKNOWN)
                .withGender(Gender.FEMALE);

        assertThat(MiniScorerTest.scoreGlossary(settled, NamePolicy.TRANSLITERATE, suggested)
                        .passed())
                .isFalse();
        assertThat(MiniScorerTest.scoreGlossary(settled, NamePolicy.TRANSLITERATE, chosen)
                        .passed())
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

        assertThat(MiniScorerTest.scoreGlossary(
                                kept,
                                NamePolicy.KEEP_ORIGINAL,
                                MiniScorerTest.entry("Velmora", "Velmora", TermType.TERM, Gender.UNKNOWN)
                                        .withLocked(true))
                        .passed())
                .isTrue();
        assertThat(MiniScorerTest.scoreGlossary(
                                kept,
                                NamePolicy.KEEP_ORIGINAL,
                                MiniScorerTest.entry("Velmora", "Вельмора", TermType.TERM, Gender.UNKNOWN))
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
        return MiniScorerTest.scoreGlossary(
                        placeExpectation(),
                        NamePolicy.TRANSLITERATE,
                        MiniScorerTest.entry("Brindle Harbour", target, TermType.PLACE, Gender.UNKNOWN))
                .passed();
    }

    @Test
    void score_multiWordPlaceTarget_isCheckedWordByWord() {
        assertThat(placeScores("Брендл Гавань")).isTrue();
        assertThat(placeScores("Брендл")).isFalse();
        assertThat(MiniScorerTest.scoreGlossary(placeExpectation(), NamePolicy.TRANSLITERATE)
                        .passed())
                .isFalse();
    }

    @Test
    void score_junkEntry_failsOnlyWhenTheGlossaryHoldsIt() {
        final GlossaryEntry junk = MiniScorerTest.entry("Nobody", "Ніхто", TermType.CHARACTER, Gender.UNKNOWN);

        assertThat(MiniScorerTest.scoreGlossary(junkExpectation(), NamePolicy.TRANSLITERATE, junk)
                        .passed())
                .isFalse();
        assertThat(MiniScorerTest.scoreGlossary(junkExpectation(), NamePolicy.TRANSLITERATE)
                        .passed())
                .isTrue();
    }
}
