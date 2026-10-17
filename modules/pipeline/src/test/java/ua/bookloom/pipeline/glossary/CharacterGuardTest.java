package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.NO_TALLY;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.entry;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.facts;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.name;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.tally;
import static ua.bookloom.pipeline.glossary.ReadingFixtures.times;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.ReadingVerdict.Decision;

/**
 * A common noun, an honorific and a title are never typed as a character, whatever kind a small model's windows gave
 * them (15j.J10); the words are invented stand-ins for what the owner's Oct 16 glossary held.
 */
class CharacterGuardTest {

    private static Decision readAsPerson(final String term, final double lowerShare, final int she) {
        return ReadingVerdict.decide(facts(
                entry(term, TermType.OTHER),
                times(3, name(TermReading.Kind.PERSON)),
                she == 0 ? NO_TALLY : tally(she, 0),
                lowerShare,
                QualityDial.BALANCED,
                "en"));
    }

    private static Decision readAsPersonWrittenCapitalised(
            final String term, final double lowerShare, final int she, final int capitalisedMidSentence) {
        final ReadingVerdict.Facts base = ReadingFixtures.facts(
                entry(term, TermType.OTHER),
                times(3, name(TermReading.Kind.PERSON)),
                she == 0 ? NO_TALLY : tally(she, 0),
                lowerShare,
                QualityDial.BALANCED,
                "en");
        return ReadingVerdict.decide(new ReadingVerdict.Facts(
                base.entry(),
                base.calls(),
                base.memberCalls(),
                base.evidence(),
                base.lowerShare(),
                base.dial(),
                base.analysisLanguage(),
                capitalisedMidSentence));
    }

    // IF a real name that is also a word were demoted, THEN a character with a weak tally would lose her gender.
    @Test
    void decide_wordWithAWeakPronounTally_staysACharacter() {
        assertThat(readAsPerson("Rose", 0.5, 2).type()).isEqualTo(TermType.CHARACTER);
    }

    @Test
    void decide_wordWrittenCapitalisedMidSentenceOften_staysACharacterWithNoTally() {
        assertThat(readAsPersonWrittenCapitalised("Rose", 0.5, 0, 3).type()).isEqualTo(TermType.CHARACTER);
    }

    @Test
    void decide_wordCapitalisedOnlyAtSentenceStarts_isDemotedAsACommonNoun() {
        assertThat(readAsPersonWrittenCapitalised("Rose", 0.5, 0, 2).type()).isNotEqualTo(TermType.CHARACTER);
    }

    // IF a word the book writes in lower case as often as not were typed a character, THEN 75 segments waited on its
    // gender (the word "name" in the owner's run).
    @ParameterizedTest(name = "{0} (lower-case share {1})")
    @CsvSource({"Government,0.5", "Detonation,0.3", "Naming,0.9", "Place,0.25"})
    void decide_wordTheBookAlsoWritesInLowerCase_isNoCharacter(final String term, final double lowerShare) {
        final Decision decision = readAsPerson(term, lowerShare, 0);

        assertThat(decision.type()).isNotEqualTo(TermType.CHARACTER);
        assertThat(decision.gender()).isEqualTo(Gender.UNKNOWN);
    }

    @Test
    void decide_stopWordTheModelCalledAPerson_isNoCharacterWhateverItsShare() {
        final Decision decision = readAsPerson("Anyway", 0.0, 0);

        assertThat(decision.type()).isNotEqualTo(TermType.CHARACTER);
    }

    @Test
    void decide_commonWordWithAClearPronounTally_staysACharacter() {
        final Decision decision = readAsPerson("Rose", 0.5, 6);

        assertThat(decision.type()).isEqualTo(TermType.CHARACTER);
        assertThat(decision.gender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    void decide_ordinaryNameWithNoLowerCaseUse_staysACharacter() {
        assertThat(readAsPerson("Brann", 0.0, 0).type()).isEqualTo(TermType.CHARACTER);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"Mr", "mrs", "Ms", "Duchess", "Prime Minister", "Lord Chancellor"})
    void decide_honorificOrTitle_isATitleEntryWithNoGender(final String term) {
        final Decision decision = readAsPerson(term, 0.0, 0);

        assertThat(decision.type()).isEqualTo(TermType.TITLE);
        assertThat(decision.kind()).isEqualTo(TermReviewReplies.Kind.TERM);
        assertThat(decision.gender()).isEqualTo(Gender.UNKNOWN);
        assertThat(decision.origin()).isNull();
    }

    @Test
    void decide_titleBeforeAName_isNotATitle() {
        assertThat(readAsPerson("Mrs Venn", 0.0, 0).type()).isEqualTo(TermType.CHARACTER);
    }
}
