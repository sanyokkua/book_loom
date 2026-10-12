package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.NameWindows.Evidence;
import ua.bookloom.pipeline.glossary.NameWindows.Window;
import ua.bookloom.pipeline.glossary.TermReviewReplies.Verdict;

/**
 * A model's gender is verified only when the book agrees (15i.N1): most of the windows it cites hold a pronoun of that
 * gender, and the book-wide pronoun tally does not favour the other gender (three or more opposing pronouns at 60 % or
 * more of the counted ones). Otherwise the gender is a suggestion, and against a favoured tally the suggestion is the
 * tally's gender. The windows are written as a string, one letter each: F a window whose pronoun the code gave the
 * name as feminine, M masculine, - none.
 */
class TermReviewRepliesGenderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROJECT = "p1";
    private static final String TERM = "Corin";

    // Most cited windows, not any one: "most" is strictly more than half of the distinct windows the verdict cites that
    // exist, and at least two of them (or the one window a term with a single window has) hold the gender's pronoun.
    // IF one matching window among many verified a gender, THEN a name followed by another person's "she" would end
    // with a woman's gender nothing marks as a guess (the owner's run).
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                // shown windows | cited | expected verified
                "FFFF|[1,2,3,4]|true",
                "FFMF|[1,2,3]|true",
                "-FF-|[1,2,3]|true",
                "FMFF|[1,2]|false",
                "FFMM|[1,2,3,4]|false",
                "FMMM|[1,2,3,4]|false",
                "FFFF|[1]|false",
                "FFFF|[1,1,1]|false",
                "FFFF|[1,2,9]|true",
                "FFFF|[0,5]|false",
                "FFFF|[]|false",
                "MMMM|[1,2,3,4]|false",
                "F|[1]|true",
                "FF|[1,2]|true",
                "FM|[1]|false",
                "FM|[1,2]|false"
            })
    void read_citedWindows_verifyOnlyWhenMostOfThemShowTheGender(
            final String shown, final String cited, final boolean expectedVerified) {
        final Verdict verdict = read("female", cited, windows(shown), tally(3, 1));

        assertThat(verdict.genderVerified()).isEqualTo(expectedVerified);
        assertThat(verdict.gender()).isEqualTo(Gender.FEMALE);
    }

    // The tally of the whole book can veto four matching windows: a favoured tally (three or more opposing pronouns at
    // 60 % or more of the counted ones) turns the model's gender into the tally's, unverified; a tally below either
    // bound does not. Rows are the she and he counts for a model that says female.
    // IF two opposing pronouns counted as favouring, THEN a name with a stray "he" could never be verified.
    @ParameterizedTest
    @CsvSource({
        "5, 9, MALE,   false", // the misattributed-she shape: she x5, he x9 (64 %)
        "2, 6, MALE,   false", // 75 %
        "2, 3, MALE,   false", // exactly 60 % with three opposing: favours
        "6, 9, MALE,   false", // exactly 60 % of fifteen
        "0, 3, MALE,   false",
        "3, 4, FEMALE, true", // 57 %: below the share
        "7, 9, FEMALE, true", // 56 %
        "1, 2, FEMALE, true", // 67 % but only two opposing: below the count
        "0, 2, FEMALE, true",
        "5, 2, FEMALE, true", // the tally agrees
        "0, 0, FEMALE, true" // no pronouns at all: nothing opposes
    })
    void read_fourMatchingWindows_areOverruledOnlyByAFavouredTally(
            final int she, final int he, final Gender expected, final boolean expectedVerified) {
        final Verdict verdict = read("female", "[1,2,3,4]", windows("FFFF"), tally(she, he));

        assertThat(verdict.gender()).isEqualTo(expected);
        assertThat(verdict.genderVerified()).isEqualTo(expectedVerified);
    }

    // IF only the female side were guarded, THEN a man's tally could never veto a model's "male" on a she-name.
    @Test
    void read_maleVerdictAgainstAFemaleFavouredTally_isTheTallysFemaleSuggestion() {
        final Verdict verdict = read("male", "[1,2,3,4]", windows("MMMM"), tally(6, 1));

        assertThat(verdict.gender()).isEqualTo(Gender.FEMALE);
        assertThat(verdict.genderVerified()).isFalse();
    }

    // The misattributed-she shape exactly: four windows with the other woman's "she", she x5, he x9, model FEMALE.
    // IF the windows alone verified, THEN the man ends FEMALE and verified, as in the owner's 26b run.
    @Test
    void read_misattributedSheShape_endsMaleSuggested() {
        final Verdict verdict = read("female", "[1,2,3,4]", windows("FFFF"), tally(5, 9));

        assertThat(verdict).extracting(Verdict::gender, Verdict::genderVerified).containsExactly(Gender.MALE, false);
    }

    // A cited window that shows too little is a suggestion that keeps the model's gender; a favoured tally replaces it
    // even then.
    @ParameterizedTest
    @CsvSource({"0, 0, FEMALE", "3, 1, FEMALE", "2, 6, MALE"})
    void read_oneCitedWindowOfFour_isASuggestionOfTheModelsGenderUnlessTheTallyFavoursTheOther(
            final int she, final int he, final Gender expected) {
        final Verdict verdict = read("female", "[1]", windows("FFFF"), tally(she, he));

        assertThat(verdict.gender()).isEqualTo(expected);
        assertThat(verdict.genderVerified()).isFalse();
    }

    // IF "unknown" were swapped for the tally's gender, THEN the code would invent a verdict the model never gave.
    @Test
    void read_unknownGenderAgainstAFavouredTally_staysUnknownAndUnverified() {
        final Verdict verdict = read("unknown", "[1,2]", windows("MM"), tally(0, 9));

        assertThat(verdict.gender()).isEqualTo(Gender.UNKNOWN);
        assertThat(verdict.genderVerified()).isFalse();
    }

    // The commit side: a man who has no gender yet takes the tally's gender as a suggestion, not a verified one.
    @Test
    void guessed_misattributedSheVerdictOnAnUnknownGender_writesMaleSuggested() {
        final GlossaryEntry now = entry();
        final Verdict verdict = read("female", "[1,2,3,4]", windows("FFFF"), tally(5, 9));

        final GlossaryEntry after = ReviewCommit.guessed(now, verdict);

        assertThat(after)
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(Gender.MALE, GenderOrigin.SUGGESTED);
    }

    // Two of three cited windows and a neutral tally are a verified gender on an entry with no gender yet.
    @Test
    void guessed_twoOfThreeCitedWindowsAndNeutralTally_writesFemaleVerified() {
        final Verdict verdict = read("female", "[1,2,3]", windows("FFM-"), tally(3, 2));

        final GlossaryEntry after = ReviewCommit.guessed(entry(), verdict);

        assertThat(after)
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(Gender.FEMALE, GenderOrigin.VERIFIED);
    }

    // IF the tally's gender were written over a gender the person set, THEN a favoured tally would undo a choice.
    @Test
    void guessed_tallyFavouredAgainstAPersonsGender_leavesThePersonsGender() {
        final GlossaryEntry persons = entry().withGender(Gender.FEMALE);
        final Verdict verdict = read("female", "[1,2,3,4]", windows("FFFF"), tally(1, 9));

        final GlossaryEntry after = ReviewCommit.guessed(persons, verdict);

        assertThat(after)
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(Gender.FEMALE, GenderOrigin.PERSON);
    }

    // A person's male gender also survives a verified-looking female verdict from a book whose tally agrees with it.
    @Test
    void guessed_verifiedFemaleVerdictOnAPersonsMaleGender_leavesThePersonsGender() {
        final GlossaryEntry persons = entry().withGender(Gender.MALE);
        final Verdict verdict = read("female", "[1,2]", windows("FF"), tally(4, 0));

        assertThat(verdict.genderVerified()).isTrue();
        assertThat(ReviewCommit.guessed(persons, verdict))
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(Gender.MALE, GenderOrigin.PERSON);
    }

    // Every verdict of a batch is judged on its own term's windows and tally.
    @Test
    void read_twoTermsInOneBatch_eachIsJudgedAgainstItsOwnTally() {
        final GlossaryEntry corin = entry();
        final GlossaryEntry mara =
                new GlossaryEntry("e2", PROJECT, "Mara", null, TermType.CHARACTER, Gender.UNKNOWN, false);
        final String reply = "{\"verdicts\":["
                + "{\"term\":\"Corin\",\"verdict\":\"name\",\"type\":\"person\",\"gender\":\"female\",\"windows\":[1,2]},"
                + "{\"term\":\"Mara\",\"verdict\":\"name\",\"type\":\"person\",\"gender\":\"female\",\"windows\":[1,2]}"
                + "]}";

        final List<Verdict> verdicts = TermReviewReplies.read(
                MAPPER,
                reply,
                Map.of("corin", corin, "mara", mara),
                Map.of(
                        "Corin", new Evidence(9, windows("FF"), tally(1, 8)),
                        "Mara", new Evidence(9, windows("FF"), tally(8, 1))));

        assertThat(verdicts)
                .extracting(verdict -> verdict.entry().term(), Verdict::gender, Verdict::genderVerified)
                .containsExactly(tuple("Corin", Gender.MALE, false), tuple("Mara", Gender.FEMALE, true));
    }

    private static GlossaryEntry entry() {
        return new GlossaryEntry("e1", PROJECT, TERM, null, TermType.CHARACTER, Gender.UNKNOWN, false);
    }

    private static Verdict read(
            final String gender, final String cited, final List<Window> shown, final PronounGender.Evidence tally) {
        final String reply = "{\"verdicts\":[{\"term\":\"" + TERM + "\",\"verdict\":\"name\",\"type\":\"person\","
                + "\"gender\":\"" + gender + "\",\"windows\":" + cited + "}]}";
        final List<Verdict> verdicts = TermReviewReplies.read(
                MAPPER, reply, Map.of("corin", entry()), Map.of(TERM, new Evidence(shown.size() + 5, shown, tally)));
        assertThat(verdicts).hasSize(1);
        return verdicts.getFirst();
    }

    private static List<Window> windows(final String pronouns) {
        return pronouns.chars()
                .mapToObj(letter -> new Window("A sentence about " + TERM + ".", pronounOf((char) letter)))
                .toList();
    }

    private static @Nullable Gender pronounOf(final char letter) {
        return switch (letter) {
            case 'F' -> Gender.FEMALE;
            case 'M' -> Gender.MALE;
            default -> null;
        };
    }

    // The code's whole-book tally: this many "she" and this many "he" after the name.
    private static PronounGender.Evidence tally(final int she, final int he) {
        return new PronounGender.Evidence(Collections.nCopies(she, "she"), Collections.nCopies(he, "he"));
    }
}
