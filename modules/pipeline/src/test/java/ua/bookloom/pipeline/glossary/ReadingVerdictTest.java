package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.IMPERATIVE;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.MALE_TEXT;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.NO_TALLY;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.WALL;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.answers;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.call;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.entry;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.facts;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.name;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.tally;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.times;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.unsure;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.untyped;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.voting;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.votingAs;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.word;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.ReadingFixtures.Answer;
import ua.bookloom.pipeline.glossary.ReadingVerdict.Decision;
import ua.bookloom.pipeline.glossary.ReadingVerdict.Facts;
import ua.bookloom.pipeline.glossary.ReadingVerdict.ReadCall;
import ua.bookloom.pipeline.glossary.TermReading.Usage;

/**
 * The code decides a term from its readings with no call (15i.N5): kind and type by the majority of the window votes
 * (a name needs two {@code name} uses, one of one at Fast); votes only break ties (15i.N4): two or more verified votes making 75 %
 * (three at 80 % under Auto) or two one-sided pieces of evidence suggest a gender with no clear tally, pooled over the
 * cluster, and a clear tally decides, verified only where the votes agree; places, organisations, things and titles
 * carry no gender; not a name
 * only on two quoted ordinary-word uses, no name use, a lower-case share of 0.2 or more and an entry never typed or
 * gendered. The windows and readings are invented and built by hand, as {@link TermReadingReplies} would leave them.
 */
class ReadingVerdictTest {

    private static Decision decide(final List<Answer> answers) {
        return ReadingVerdict.decide(facts(untyped(), answers, NO_TALLY, 0.0, QualityDial.BALANCED, "en"));
    }

    private static Decision decide(final List<Answer> answers, final PronounGender.Evidence tally) {
        return ReadingVerdict.decide(facts(untyped(), answers, tally, 0.0, QualityDial.BALANCED, "en"));
    }

    private static Decision decideWord(final GlossaryEntry entry, final List<Answer> answers, final double share) {
        return ReadingVerdict.decide(facts(entry, answers, NO_TALLY, share, QualityDial.BALANCED, "en"));
    }

    // --- kind and type -----------------------------------------------------------------------------------------------

    // IF one unsure window outweighed three that read a person's name, THEN a character would stay untyped.
    @Test
    void decide_mostWindowsReadAPersonsName_isACharacterName() {
        final Decision decision = decide(answers(times(3, name(TermReading.Kind.PERSON)), List.of(unsure())));

        assertThat(decision.kind()).isEqualTo(TermReviewReplies.Kind.NAME);
        assertThat(decision.type()).isEqualTo(TermType.CHARACTER);
    }

    // The kind most windows give is the type: an organisation and a named thing are both a glossary term.
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"PERSON,CHARACTER", "PLACE,PLACE", "ORGANISATION,TERM", "THING,TERM"})
    void decide_nameWindowsMostlyOfOneKind_takeThatKindsType(final TermReading.Kind kind, final TermType type) {
        final Decision decision = decide(answers(times(3, name(kind)), List.of(name(TermReading.Kind.OTHER))));

        assertThat(decision.type()).isEqualTo(type);
    }

    // IF a title's "he" gave it a gender, THEN "Captain" would be written as a man for every captain in the book.
    @Test
    void decide_mostWindowsReadATitle_isATitleWithNoGender() {
        final Answer title =
                new Answer(MALE_TEXT, Usage.TITLE, TermReading.Kind.PERSON, "He left before noon", Gender.MALE);

        final Decision decision = decide(times(3, title));

        assertThat(decision.type()).isEqualTo(TermType.TITLE);
        assertThat(decision.gender()).isEqualTo(Gender.UNKNOWN);
        assertThat(decision.origin()).isNull();
    }

    // A name needs two windows that read it as one; at Fast, which shows few windows, one window of one is enough.
    @ParameterizedTest(name = "{0}: {1} name, {2} unsure -> {3} {4}")
    @CsvSource({
        "BALANCED,1,0,UNSURE,OTHER",
        "MAX,1,0,UNSURE,OTHER",
        "BALANCED,2,0,NAME,CHARACTER",
        "BALANCED,2,1,NAME,CHARACTER",
        "FAST,1,0,NAME,CHARACTER",
        "FAST,1,1,UNSURE,OTHER"
    })
    void decide_nameUses_needTwoOrOneOfOneAtFast(
            final QualityDial dial,
            final int names,
            final int unsure,
            final TermReviewReplies.Kind kind,
            final TermType type) {
        final List<Answer> answers = answers(times(names, name(TermReading.Kind.PERSON)), times(unsure, unsure()));

        final Decision decision = ReadingVerdict.decide(facts(untyped(), answers, NO_TALLY, 0.0, dial, "en"));

        assertThat(decision.kind()).isEqualTo(kind);
        assertThat(decision.type()).isEqualTo(type);
    }

    // --- gender ------------------------------------------------------------------------------------------------------

    // Verified at two or more verified votes making 75 % or more; under Auto, where the code reads no pronoun of its
    // own, three at 80 %. Anything less is a suggestion of the majority's gender.
    // With no pronoun of the code's own, votes never verify (15i.N4): at the thresholds, or as two pieces of one-sided
    // evidence, they suggest; a single vote or votes both ways give nothing.
    @ParameterizedTest(name = "{0} male, {1} female, language {2} -> {3} {4}")
    @CsvSource(
            value = {
                "2,0,en,MALE,SUGGESTED",
                "0,2,en,FEMALE,SUGGESTED",
                "3,1,en,MALE,SUGGESTED",
                "1,0,en,UNKNOWN,NONE",
                "2,1,en,UNKNOWN,NONE",
                "3,0,,MALE,SUGGESTED",
                "4,1,,MALE,SUGGESTED",
                "2,0,,MALE,SUGGESTED",
                "3,1,,UNKNOWN,NONE"
            },
            nullValues = "NONE")
    void decide_votesWithNoTally_onlySuggestAGender(
            final int male,
            final int female,
            @Nullable final String language,
            final Gender gender,
            @Nullable final GenderOrigin origin) {
        final List<Answer> answers = answers(
                times(male, voting(Gender.MALE)),
                times(female, voting(Gender.FEMALE)),
                times(2, name(TermReading.Kind.PERSON)));

        final Decision decision =
                ReadingVerdict.decide(facts(untyped(), answers, NO_TALLY, 0.0, QualityDial.BALANCED, language));

        assertThat(decision.gender()).isEqualTo(gender);
        assertThat(decision.origin()).isEqualTo(origin);
    }

    // One person, one gender (15i.N2): the short name's one vote and the full name's one vote are two votes of one
    // person, so together they suggest what neither suggests alone.
    @Test
    void decide_oneVoteHereAndOneInAClusterMember_poolIntoASuggestedGender() {
        final ReadCall own = call("Brann", List.of(voting(Gender.MALE), name(TermReading.Kind.PERSON)));
        final ReadCall member = call("Brann Holt", List.of(name(TermReading.Kind.PERSON), voting(Gender.MALE)));
        final Facts facts = new Facts(
                untyped(),
                List.of(own),
                List.of(member),
                new NameWindows.Evidence(2, List.of(), NO_TALLY),
                0.0,
                QualityDial.BALANCED,
                "en");

        final Decision decision = ReadingVerdict.decide(facts);

        assertThat(decision.gender()).isEqualTo(Gender.MALE);
        assertThat(decision.origin()).isEqualTo(GenderOrigin.SUGGESTED);
    }

    // IF the windows outvoted a book whose pronouns favour the other gender (three or more at 60 % or more, 15i.N1),
    // THEN a misread "he" would be enforced; the tally's gender stays, as a suggestion, and no call is spent.
    @Test
    void decide_votesAgainstAFavouredButNotDominantTally_keepTheTallysGenderSuggested() {
        final Decision decision =
                decide(answers(times(3, voting(Gender.MALE)), List.of(name(TermReading.Kind.PERSON))), tally(3, 2));

        assertThat(decision.gender()).isEqualTo(Gender.FEMALE);
        assertThat(decision.origin()).isEqualTo(GenderOrigin.SUGGESTED);
    }

    @Test
    void decide_votesAgreeWithADominantTally_areVerified() {
        final Decision decision =
                decide(answers(times(2, voting(Gender.FEMALE)), List.of(name(TermReading.Kind.PERSON))), tally(4, 0));

        assertThat(decision.gender()).isEqualTo(Gender.FEMALE);
        assertThat(decision.origin()).isEqualTo(GenderOrigin.VERIFIED);
    }

    // IF a place's or a ship's "he" gave it a gender, THEN a city would be agreed with as a man; places,
    // organisations and things carry no gender, whatever the votes and the tally say.
    @ParameterizedTest(name = "{0}")
    @CsvSource({"PLACE", "ORGANISATION", "THING"})
    void decide_votesOnANonPerson_giveNoGender(final TermReading.Kind kind) {
        final Decision decision = decide(times(3, votingAs(kind)), tally(4, 0));

        assertThat(decision.gender()).isEqualTo(Gender.UNKNOWN);
        assertThat(decision.origin()).isNull();
    }

    // The person's gender is never re-decided.
    @Test
    void decide_personSetGenderAgainstVotesAndTally_decidesNoGender() {
        final GlossaryEntry person = entry("Brann", TermType.CHARACTER).withGender(Gender.FEMALE);
        final List<Answer> answers = answers(times(3, voting(Gender.MALE)), List.of(name(TermReading.Kind.PERSON)));

        final Decision decision =
                ReadingVerdict.decide(facts(person, answers, tally(4, 0), 0.0, QualityDial.BALANCED, "en"));

        assertThat(decision.gender()).isEqualTo(Gender.UNKNOWN);
    }

    // --- not a name --------------------------------------------------------------------------------------------------

    // Two ordinary-word uses quoted from their own windows remove a word the book writes in lower case a fifth of the
    // time or more; below a fifth the capital is the book's habit for a name, and the word stays for the person.
    @ParameterizedTest(name = "lower-case share {0} -> {1}")
    @CsvSource({"0.19,UNSURE", "0.2,NOT_A_NAME", "0.6,NOT_A_NAME"})
    void decide_twoQuotedWordUses_areNotANameFromALowerCaseShareOfAFifth(
            final double share, final TermReviewReplies.Kind kind) {
        final List<Answer> answers =
                answers(times(2, word(IMPERATIVE, "Mark the spot")), List.of(word(WALL, "a mark on the wall")));

        final Decision decision = decideWord(entry("Mark", TermType.OTHER), answers, share);

        assertThat(decision.kind()).isEqualTo(kind);
    }

    // IF one quoted word use removed an entry, THEN one misread window would delete a name.
    @Test
    void decide_oneQuotedWordUseAndOneUnquoted_isNoRemoval() {
        final List<Answer> answers = List.of(word(IMPERATIVE, "Mark the spot"), word(WALL, "none of this is there"));

        final Decision decision = decideWord(entry("Mark", TermType.OTHER), answers, 0.6);

        assertThat(decision.kind()).isNotEqualTo(TermReviewReplies.Kind.NOT_A_NAME);
    }

    // A quote counts only in the window it answers: the wall's phrase copied into the imperative's answer is no
    // second use.
    @Test
    void decide_wordQuoteCopiedFromAnotherWindow_isNotASecondUse() {
        final List<Answer> answers = List.of(word(IMPERATIVE, "a mark on the wall"), word(WALL, "a mark on the wall"));

        final Decision decision = decideWord(entry("Mark", TermType.OTHER), answers, 0.6);

        assertThat(decision.kind()).isNotEqualTo(TermReviewReplies.Kind.NOT_A_NAME);
    }

    // IF three ordinary-word windows outvoted one that reads the bartender's name, THEN the bartender would be deleted
    // with the common word; one name use blocks the removal.
    @Test
    void decide_quotedWordUsesAndOneNameUse_isNoRemoval() {
        final List<Answer> answers =
                answers(times(3, word(IMPERATIVE, "Mark the spot")), List.of(name(TermReading.Kind.PERSON)));

        final Decision decision = decideWord(entry("Mark", TermType.OTHER), answers, 0.6);

        assertThat(decision.kind()).isNotEqualTo(TermReviewReplies.Kind.NOT_A_NAME);
    }

    // The review's removal rule holds here too: an entry a person, a scan or a seed already typed or gave a gender is
    // never judged "not a name".
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"CHARACTER,UNKNOWN", "PLACE,UNKNOWN", "OTHER,MALE"})
    void decide_quotedWordUsesOnATypedOrGenderedEntry_isNoRemoval(final TermType type, final Gender gender) {
        final GlossaryEntry held = entry("Mark", type).withSuggestedGender(gender);
        final List<Answer> answers = times(3, word(IMPERATIVE, "Mark the spot"));

        final Decision decision = decideWord(held, answers, 0.6);

        assertThat(decision.kind()).isNotEqualTo(TermReviewReplies.Kind.NOT_A_NAME);
    }
}
