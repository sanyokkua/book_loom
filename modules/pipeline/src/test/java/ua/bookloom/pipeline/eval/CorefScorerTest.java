package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import ua.bookloom.pipeline.eval.CorefReport.Outcome;
import ua.bookloom.pipeline.eval.CorefWindow.Pronoun;
import ua.bookloom.pipeline.eval.CorefWindow.Referent;

/** The coreference scorer (15i.E2) turns a reader's quoted votes into right, wrong and abstained windows. */
class CorefScorerTest {

    private static final CorefWindow PAIR = window(
            "Ilsabet",
            "Ilsabet looked at Tovrin. She nodded, and he smiled.",
            pronoun("She nodded", "She", Referent.TARGET),
            pronoun("he smiled", "he", Referent.OTHER));

    private static final CorefWindow PLACE = window(
            "Harrowmere",
            "Harrowmere had always looked after her fishermen.",
            pronoun("her fishermen", "her", Referent.PLACE_PEOPLE));

    private static final CorefWindow CROWDED = window(
            "Ilsabet",
            "Ilsabet met Tovrin. She smiled, and she saw that he was tired.",
            pronoun("She smiled", "She", Referent.TARGET),
            pronoun("she saw", "she", Referent.TARGET),
            pronoun("he was tired", "he", Referent.OTHER));

    private static CorefWindow window(final String target, final String text, final Pronoun... pronouns) {
        return new CorefWindow("w-" + target, target, "plain", List.of(text), List.of(pronouns));
    }

    private static Pronoun pronoun(final String quote, final String word, final Referent referent) {
        return new Pronoun(quote, word, referent, "someone", false);
    }

    private static CorefReader answering(final CorefClaim... claims) {
        return (term, text) -> List.of(claims);
    }

    private static CorefClaim vote(final String quote) {
        return new CorefClaim(quote, true);
    }

    private static List<Outcome> outcomes(final CorefReport report) {
        return report.rows().stream().map(CorefReport.Row::outcome).toList();
    }

    @Test
    void score_voteOnAPronounOfTheTarget_isRightReferent() {
        final CorefReport report = CorefScorer.score(List.of(PAIR), answering(vote("She nodded")));

        assertThat(outcomes(report)).containsExactly(Outcome.RIGHT_REFERENT);
        assertThat(report.rightReferent()).isEqualTo(1);
        assertThat(report.wrongVotes()).isZero();
        assertThat(report.abstentions()).isZero();
        assertThat(report.wrongVoteRate()).isZero();
    }

    @Test
    void score_barePronounWordThatOnlyTheTargetsSnippetHolds_isRightReferent() {
        final CorefReport report = CorefScorer.score(List.of(PAIR), answering(vote("She")));

        assertThat(outcomes(report)).containsExactly(Outcome.RIGHT_REFERENT);
    }

    @Test
    void score_voteOnAPronounOfAnotherCharacter_isWrongVote() {
        final CorefReport report = CorefScorer.score(List.of(PAIR), answering(vote("he smiled")));

        assertThat(outcomes(report)).containsExactly(Outcome.WRONG_VOTE);
        assertThat(report.wrongVotes()).isEqualTo(1);
        assertThat(report.wrongVoteRate()).isEqualTo(1.0);
    }

    // IF a place's "her" means its people, THEN a vote that it names the place is a wrong gender vote.
    @Test
    void score_voteOnAPlacesPeoplePronoun_isWrongVote() {
        final CorefReport report = CorefScorer.score(List.of(PLACE), answering(vote("her fishermen")));

        assertThat(outcomes(report)).containsExactly(Outcome.WRONG_VOTE);
    }

    // IF the quote is in the window but is no gold pronoun, THEN the vote cannot be a right referent.
    @Test
    void score_voteOnTextThatIsNoGoldPronoun_isWrongVote() {
        final CorefReport report = CorefScorer.score(List.of(PAIR), answering(vote("looked at Tovrin")));

        assertThat(outcomes(report)).containsExactly(Outcome.WRONG_VOTE);
    }

    // IF a bare pronoun fits a pronoun of the target and one of another character, THEN it cannot vote safely.
    @Test
    void score_quoteThatFitsAPronounOfTheTargetAndOfAnotherCharacter_isWrongVote() {
        final CorefReport report = CorefScorer.score(List.of(CROWDED), answering(vote("he")));

        assertThat(outcomes(report)).containsExactly(Outcome.WRONG_VOTE);
    }

    @Test
    void score_noClaims_isAbstention() {
        final CorefReport report = CorefScorer.score(List.of(PAIR), answering());

        assertThat(outcomes(report)).containsExactly(Outcome.ABSTENTION);
        assertThat(report.abstentions()).isEqualTo(1);
        assertThat(report.wrongVoteRate()).isZero();
    }

    @Test
    void score_claimThatSaysItDoesNotReferToTheTerm_isAbstention() {
        final CorefReport report = CorefScorer.score(List.of(PAIR), answering(new CorefClaim("he smiled", false)));

        assertThat(outcomes(report)).containsExactly(Outcome.ABSTENTION);
    }

    // IF the model quotes words that are not in the window, THEN the vote is dropped and the window abstains.
    @Test
    void score_voteWhoseQuoteIsNotInTheWindow_isAbstentionNotAWrongVote() {
        final CorefReport report = CorefScorer.score(List.of(PAIR), answering(vote("she shouted")));

        assertThat(outcomes(report)).containsExactly(Outcome.ABSTENTION);
        assertThat(report.wrongVotes()).isZero();
    }

    @Test
    void score_voteOutsideTheWindowBesideARightVote_isRightReferent() {
        final CorefReport report = CorefScorer.score(List.of(PAIR), answering(vote("she shouted"), vote("She nodded")));

        assertThat(outcomes(report)).containsExactly(Outcome.RIGHT_REFERENT);
    }

    @Test
    void score_rightVoteAndWrongVoteInOneWindow_isWrongVote() {
        final CorefReport report = CorefScorer.score(List.of(PAIR), answering(vote("She nodded"), vote("he smiled")));

        assertThat(outcomes(report)).containsExactly(Outcome.WRONG_VOTE);
    }

    @Test
    void score_reader_isAskedWithTheTargetAndTheWindowText() {
        final List<String> asked = new ArrayList<>();

        CorefScorer.score(List.of(PAIR), (term, text) -> {
            asked.add(term);
            asked.add(text);
            return List.of();
        });

        assertThat(asked).containsExactly("Ilsabet", "Ilsabet looked at Tovrin. She nodded, and he smiled.");
    }

    // 2 right, 1 wrong, 1 abstained: the wrong-vote rate counts only the windows that voted.
    @Test
    void score_fourWindows_countsAndWrongVoteRateAreTheHandCountedOnes() {
        final CorefWindow second = window(
                "Tovrin",
                "Tovrin looked at Ilsabet. He nodded, and she smiled.",
                pronoun("He nodded", "He", Referent.TARGET),
                pronoun("she smiled", "she", Referent.OTHER));
        final CorefWindow silent = window("Odrek", "Odrek waited.");
        final Map<String, List<CorefClaim>> claims = Map.of(
                "Ilsabet", List.of(vote("She nodded")),
                "Tovrin", List.of(vote("He nodded")),
                "Harrowmere", List.of(vote("her fishermen")));

        final CorefReport report = CorefScorer.score(
                List.of(PAIR, second, PLACE, silent), (term, text) -> claims.getOrDefault(term, List.of()));

        assertThat(outcomes(report))
                .containsExactly(
                        Outcome.RIGHT_REFERENT, Outcome.RIGHT_REFERENT, Outcome.WRONG_VOTE, Outcome.ABSTENTION);
        assertThat(report.rightReferent()).isEqualTo(2);
        assertThat(report.wrongVotes()).isEqualTo(1);
        assertThat(report.abstentions()).isEqualTo(1);
        assertThat(report.wrongVoteRate()).isCloseTo(0.3333, within(0.0001));
    }

    @Test
    void score_noWindows_ratesAreZero() {
        final CorefReport report = CorefScorer.score(List.of(), answering(vote("She nodded")));

        assertThat(report.rows()).isEmpty();
        assertThat(report.wrongVoteRate()).isZero();
    }

    // The fixture: a reader that votes exactly the target's pronouns is right in the 15 windows that have one.
    @Test
    void score_fixtureWithAReaderThatVotesTheTargetsPronouns_has15RightNoWrongAndAbstainsElsewhere() {
        final Map<String, CorefWindow> byText = byText();
        final CorefReader oracle = (term, text) -> byText.get(text).pronouns().stream()
                .filter(pronoun -> pronoun.referent() == Referent.TARGET)
                .map(pronoun -> vote(pronoun.quote()))
                .toList();

        final CorefReport report = CorefScorer.score(CorefCorpus.windows(), oracle);

        assertThat(report.rows()).hasSize(31);
        assertThat(report.rightReferent()).isEqualTo(15);
        assertThat(report.wrongVotes()).isZero();
        assertThat(report.abstentions()).isEqualTo(16);
        assertThat(report.wrongVoteRate()).isZero();
    }

    // The fixture: a reader that votes every pronoun it sees is wrong in the 22 windows that hold a pronoun of
    // anyone but the target, and right in the 9 that hold only the target's.
    @Test
    void score_fixtureWithAReaderThatVotesEveryPronoun_has22WrongVotesAnd9Right() {
        final Map<String, CorefWindow> byText = byText();
        final CorefReader everything = (term, text) -> byText.get(text).pronouns().stream()
                .map(pronoun -> vote(pronoun.quote()))
                .toList();

        final CorefReport report = CorefScorer.score(CorefCorpus.windows(), everything);

        assertThat(report.rightReferent()).isEqualTo(9);
        assertThat(report.wrongVotes()).isEqualTo(22);
        assertThat(report.abstentions()).isZero();
        assertThat(report.wrongVoteRate()).isCloseTo(0.7097, within(0.0001));
    }

    @Test
    void score_fixtureWithAReaderThatAlwaysAnswersNone_abstainsEverywhere() {
        final CorefReport report = CorefScorer.score(CorefCorpus.windows(), answering());

        assertThat(report.abstentions()).isEqualTo(31);
        assertThat(report.wrongVoteRate()).isZero();
    }

    private static Map<String, CorefWindow> byText() {
        return CorefCorpus.windows().stream().collect(Collectors.toMap(CorefWindow::text, Function.identity()));
    }
}
