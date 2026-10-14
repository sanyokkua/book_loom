package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.NO_TALLY;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.answers;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.facts;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.name;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.tally;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.times;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.untyped;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.voting;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.ReadingFixtures.Answer;
import ua.bookloom.pipeline.glossary.ReadingVerdict.Decision;

/**
 * Votes that only break ties (15i.N5): the coreference round of 15i.N4 found 10 % of 26b's and 20 % of e4b's votes
 * wrong against a 5 % gate, so the code's clear pronoun tally decides, verified only where the verified votes agree,
 * and with no clear tally the votes at the deciding thresholds give a suggestion only. The readings are invented
 * ({@link ReadingFixtures}).
 */
class ReadingVotesTest {

    private static Decision breakingTies(final List<Answer> answers, final PronounGender.Evidence tally) {
        return ReadingVerdict.decide(facts(untyped(), answers, tally, 0.0, QualityDial.BALANCED, "en"));
    }

    // A clear tally (three or more pronouns at 60 %) decides the gender; it is written verified only when the verified
    // votes agree with it and none opposes it (15i.N gate: the tally's nearest-name attribution alone wrote a demon's
    // "it" as a verified "he"), else as a suggestion, even at six pronouns of one gender.
    @ParameterizedTest(name = "{0} male, {1} female votes, tally she {2} he {3} -> {4} {5}")
    @CsvSource({
        "3,0,4,0,FEMALE,SUGGESTED",
        "0,0,3,2,FEMALE,SUGGESTED",
        "0,0,0,5,MALE,SUGGESTED",
        "3,0,0,3,MALE,VERIFIED",
        "1,0,0,3,MALE,VERIFIED",
        "0,1,3,0,FEMALE,VERIFIED",
        "1,1,0,4,MALE,SUGGESTED",
        "2,1,0,4,MALE,SUGGESTED",
        "0,0,0,6,MALE,SUGGESTED"
    })
    void decide_tieBreakingAndAClearTally_isTheTallysGenderVerifiedOnlyWhenTheVotesAgree(
            final int male,
            final int female,
            final int she,
            final int he,
            final Gender gender,
            final GenderOrigin origin) {
        final Decision decision = breakingTies(
                answers(
                        times(male, voting(Gender.MALE)),
                        times(female, voting(Gender.FEMALE)),
                        times(2, name(TermReading.Kind.PERSON))),
                tally(she, he));

        assertThat(decision.gender()).isEqualTo(gender);
        assertThat(decision.origin()).isEqualTo(origin);
    }

    // With no clear tally the votes break the tie, but only as a suggestion: at the thresholds suggested, never
    // verified. Below them, two or more pieces of evidence, votes and pronouns together, all pointing one way still
    // suggest that gender (15i.N gate: minor characters with "he" twice and nothing against were left unknown); a
    // single
    // piece, or evidence both ways, gives nothing.
    @ParameterizedTest(name = "{0} male, {1} female, tally she {2} he {3} -> {4}")
    @CsvSource(
            value = {
                "3,0,0,0,MALE,SUGGESTED",
                "2,0,1,1,MALE,SUGGESTED",
                "2,1,0,0,UNKNOWN,NONE",
                "1,0,0,0,UNKNOWN,NONE",
                "1,0,0,1,MALE,SUGGESTED",
                "0,0,0,2,MALE,SUGGESTED",
                "0,0,2,0,FEMALE,SUGGESTED",
                "1,0,1,0,UNKNOWN,NONE",
                "0,0,1,1,UNKNOWN,NONE"
            },
            nullValues = "NONE")
    void decide_tieBreakingWithNoClearTally_votesAtTheThresholdsAreOnlySuggested(
            final int male,
            final int female,
            final int she,
            final int he,
            final Gender gender,
            @Nullable final GenderOrigin origin) {
        final Decision decision = breakingTies(
                answers(
                        times(male, voting(Gender.MALE)),
                        times(female, voting(Gender.FEMALE)),
                        times(2, name(TermReading.Kind.PERSON))),
                tally(she, he));

        assertThat(decision.gender()).isEqualTo(gender);
        assertThat(decision.origin()).isEqualTo(origin);
    }

    // Kind and type do not depend on the mode: the same windows make the same character.
    @Test
    void decide_tieBreaking_typesTheNameAsTheDecidingModeDoes() {
        final Decision decision = breakingTies(times(3, name(TermReading.Kind.PERSON)), NO_TALLY);

        assertThat(decision.kind()).isEqualTo(TermReviewReplies.Kind.NAME);
        assertThat(decision.type()).isEqualTo(TermType.CHARACTER);
    }

    // A cluster pools the readings of its spellings, and two spellings' calls may show the same window: one quote in
    // one paragraph is one vote, whichever call it came in (15i review).
    @Test
    void votes_sameWindowReadForTwoSpellings_countsOnce() {
        final List<ReadingVerdict.ReadCall> calls = List.of(
                ReadingFixtures.call("Brann", times(2, voting(Gender.MALE))),
                ReadingFixtures.call("Brann", times(2, voting(Gender.MALE))));

        assertThat(ReadingCounts.votes(calls, Gender.MALE)).isEqualTo(2);
    }
}
