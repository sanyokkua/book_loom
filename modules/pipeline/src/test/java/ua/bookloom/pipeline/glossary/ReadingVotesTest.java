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
import ua.bookloom.pipeline.glossary.ReadingVotes.VoteMode;

/**
 * Votes that only break ties (15i.N5), the mode every model gets today: the coreference round of 15i.N4 found 10 % of
 * 26b's and 20 % of e4b's votes wrong against a 5 % gate, so the code's clear pronoun tally decides and is written
 * verified, and with no clear tally the votes at the deciding thresholds give a suggestion only. The readings are
 * invented ({@link ReadingFixtures}).
 */
class ReadingVotesTest {

    private static Decision breakingTies(final List<Answer> answers, final PronounGender.Evidence tally) {
        return ReadingVerdict.decide(
                facts(untyped(), answers, tally, 0.0, QualityDial.BALANCED, "en"), VoteMode.TIEBREAK);
    }

    // No model has passed the coreference gate (26b 10 %, e4b 20 % wrong votes), so no model's votes decide.
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            value = {"gemma4:26b", "gemma4:e4b-mlx", "NONE"},
            nullValues = "NONE")
    void modeFor_everyModelToday_isTieBreaking(@Nullable final String modelId) {
        assertThat(ReadingVotes.modeFor(modelId)).isEqualTo(VoteMode.TIEBREAK);
    }

    // A clear tally (three or more pronouns at 60 %) decides and is written verified, whatever the votes say, and no
    // tiebreak call is spent on a majority against it.
    @ParameterizedTest(name = "{0} male votes, tally she {1} he {2} -> {3}")
    @CsvSource({"3,4,0,FEMALE", "0,3,2,FEMALE", "3,0,3,MALE", "0,0,5,MALE"})
    void decide_tieBreakingAndAClearTally_isTheTallysGenderVerified(
            final int maleVotes, final int she, final int he, final Gender gender) {
        final Decision decision = breakingTies(
                answers(times(maleVotes, voting(Gender.MALE)), times(2, name(TermReading.Kind.PERSON))),
                tally(she, he));

        assertThat(decision.gender()).isEqualTo(gender);
        assertThat(decision.origin()).isEqualTo(GenderOrigin.VERIFIED);
        assertThat(decision.needsTiebreak()).isFalse();
    }

    // With no clear tally the votes break the tie, but only as a suggestion: at the thresholds suggested, below them
    // nothing, never verified.
    @ParameterizedTest(name = "{0} male, {1} female, tally she {2} he {3} -> {4}")
    @CsvSource(
            value = {"3,0,0,0,MALE,SUGGESTED", "2,0,1,1,MALE,SUGGESTED", "2,1,0,0,UNKNOWN,NONE", "1,0,0,0,UNKNOWN,NONE"
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
        assertThat(decision.needsTiebreak()).isFalse();
    }

    // Kind and type do not depend on the mode: the same windows make the same character.
    @Test
    void decide_tieBreaking_typesTheNameAsTheDecidingModeDoes() {
        final Decision decision = breakingTies(times(3, name(TermReading.Kind.PERSON)), NO_TALLY);

        assertThat(decision.kind()).isEqualTo(TermReviewReplies.Kind.NAME);
        assertThat(decision.type()).isEqualTo(TermType.CHARACTER);
    }
}
