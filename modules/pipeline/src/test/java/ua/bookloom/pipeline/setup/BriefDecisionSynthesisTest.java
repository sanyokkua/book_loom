package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.FieldEvidence.Basis;
import ua.bookloom.pipeline.setup.BriefDecision.Text;
import ua.bookloom.pipeline.setup.BriefReplies.Answer;
import ua.bookloom.pipeline.setup.Knowledge.Reason;

/**
 * The genre, audience and voice of the brief (15i.B4), decided by the synthesis call and kept only on a basis the code
 * checks: accepted knowledge that agrees with the value, two cited windows with a reading, or one cited window with a
 * quote when knowledge agrees or when the whole story is one window; a value outside the field's list or length is
 * refused whatever its basis. Split from {@link BriefDecisionTest} for its length.
 */
class BriefDecisionSynthesisTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String Q1 = "The lamps were lit early that winter";
    private static final String Q2 = "Nobody in the lane spoke of the mill";
    private static final String Q3 = "She counted the coins twice before bed";
    private static final Knowledge UNKNOWN = Knowledge.unknown(Reason.NOT_KNOWN);
    private static final Knowledge KNOWS_A_MYSTERY = known("mystery novel", "adult readers", "late Victorian, formal");
    private static final Knowledge KNOWS_A_ROMANCE = known("romance", "young readers", "modern and breezy");

    private static Knowledge known(final String genre, final String audience, final String era) {
        return Knowledge.known(new BookKnowledgeReply(
                true,
                "The Lantern Ledger",
                "Orsolya Venn",
                1898,
                genre,
                audience,
                era,
                "first person",
                "A clerk keeps the accounts of a lamp works.",
                0.9));
    }

    private static Map<BriefField, Answer> says(final BriefField field, final String value, final String quote) {
        final Map<BriefField, Answer> window = new EnumMap<>(BriefField.class);
        window.put(field, new Answer(value, quote));
        return window;
    }

    private static Map<BriefField, Answer> nothing() {
        return new EnumMap<>(BriefField.class);
    }

    @SafeVarargs
    private static List<@Nullable Map<BriefField, Answer>> windows(final @Nullable Map<BriefField, Answer>... read) {
        return new ArrayList<>(Arrays.asList(read));
    }

    // Four windows whose genre, audience and voice readings each carry their own quote; the fourth has none.
    private static List<@Nullable Map<BriefField, Answer>> readings(final BriefField field, final String value) {
        return windows(says(field, value, Q1), says(field, value, Q2), says(field, value, Q3), nothing());
    }

    private static JsonNode reply(final String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException cause) {
            throw new AssertionError(json, cause);
        }
    }

    private static JsonNode synthesis(
            final String field, final boolean knowledge, final String passages, final String value) {
        return reply("{\"" + field + "\":{\"basis\":{\"knowledge\":" + knowledge + ",\"passages\":[" + passages
                + "]},\"value\":\"" + value + "\"}}");
    }

    // --- genre, audience, voice: the synthesis, held to a basis the code can check ---

    @Test
    void synthesized_basisAcceptedKnowledgeThatAgrees_standsFromWhatTheModelKnows() {
        final Text decided = BriefDecision.synthesized(
                BriefField.GENRE,
                synthesis("genre", true, "", "Mystery"),
                KNOWS_A_MYSTERY,
                readings(BriefField.GENRE, "a slow village mystery"),
                false);

        assertThat(decided.value()).isEqualTo("Mystery");
        assertThat(decided.evidence().basis()).isEqualTo(Basis.KNOWLEDGE);
        assertThat(decided.evidence().describe()).isEqualTo("from what the model knows of this book");
    }

    // IF a cited knowledge counted when the book is unknown, THEN an invented book would get the model's guess.
    @Test
    void synthesized_basisKnowledgeButTheBookIsUnknown_isEmptyAndUncertain() {
        final Text decided = BriefDecision.synthesized(
                BriefField.GENRE,
                synthesis("genre", true, "", "Mystery"),
                UNKNOWN,
                readings(BriefField.GENRE, "x"),
                false);

        assertThat(decided.value()).isNull();
        assertThat(decided.evidence().isKept()).isFalse();
    }

    // IF the model could cite knowledge for a value the knowledge does not hold, THEN the citation would prove nothing.
    @Test
    void synthesized_basisKnowledgeThatSaysAnotherGenre_isEmptyAndUncertain() {
        final Text decided = BriefDecision.synthesized(
                BriefField.GENRE,
                synthesis("genre", true, "", "Mystery"),
                KNOWS_A_ROMANCE,
                readings(BriefField.GENRE, "x"),
                false);

        assertThat(decided.value()).isNull();
        assertThat(decided.evidence().isKept()).isFalse();
    }

    @Test
    void synthesized_basisTwoWindowsWithReadings_standsFromTwoOfFourPassages() {
        final Text decided = BriefDecision.synthesized(
                BriefField.GENRE,
                synthesis("genre", false, "1,3", "Mystery"),
                UNKNOWN,
                readings(BriefField.GENRE, "a slow village mystery"),
                false);

        assertThat(decided.value()).isEqualTo("Mystery");
        assertThat(decided.evidence().basis()).isEqualTo(Basis.PASSAGES);
        assertThat(decided.evidence().quotes()).containsExactly(Q1, Q3);
        assertThat(decided.evidence().agreeing()).isEqualTo(2);
        assertThat(decided.evidence().samples()).isEqualTo(4);
        assertThat(decided.evidence().describe()).isEqualTo("from 2 of 4 passages");
    }

    @Test
    void synthesized_oneWindowWithAQuoteAndKnowledgeThatAgrees_stands() {
        final Text decided = BriefDecision.synthesized(
                BriefField.GENRE,
                synthesis("genre", false, "2", "Mystery"),
                KNOWS_A_MYSTERY,
                readings(BriefField.GENRE, "a slow village mystery"),
                false);

        assertThat(decided.value()).isEqualTo("Mystery");
        assertThat(decided.evidence().isKept()).isTrue();
        assertThat(decided.evidence().quotes()).containsExactly(Q2);
    }

    @Test
    void synthesized_oneWindowWithAQuoteAndKnowledgeThatDisagrees_isEmptyAndUncertain() {
        final Text decided = BriefDecision.synthesized(
                BriefField.GENRE,
                synthesis("genre", false, "2", "Mystery"),
                KNOWS_A_ROMANCE,
                readings(BriefField.GENRE, "a slow village mystery"),
                false);

        assertThat(decided.value()).isNull();
        assertThat(decided.evidence().isKept()).isFalse();
    }

    // IF one window of four could decide, THEN a single odd stretch of the book would set the genre.
    @Test
    void synthesized_oneWindowOfFourAlone_isEmptyAndUncertainWithItsCount() {
        final Text decided = BriefDecision.synthesized(
                BriefField.GENRE,
                synthesis("genre", false, "2", "Mystery"),
                UNKNOWN,
                readings(BriefField.GENRE, "a slow village mystery"),
                false);

        assertThat(decided.value()).isNull();
        assertThat(decided.evidence().isKept()).isFalse();
        assertThat(decided.evidence().agreeing()).isEqualTo(1);
        assertThat(decided.evidence().samples()).isEqualTo(4);
    }

    // A short book is read in one window: there is no second window to agree, so its one quoted reading stands.
    @Test
    void synthesized_wholeStoryReadInOneWindow_itsQuotedReadingStandsFromOneOfOnePassages() {
        final Text decided = BriefDecision.synthesized(
                BriefField.GENRE,
                synthesis("genre", false, "1", "Mystery"),
                UNKNOWN,
                windows(says(BriefField.GENRE, "a slow village mystery", Q1)),
                true);

        assertThat(decided.value()).isEqualTo("Mystery");
        assertThat(decided.evidence().describe()).isEqualTo("from 1 of 1 passages");
    }

    // IF a story of one and a half windows read as one window counted as the whole book, THEN one cited reading of a
    // stretch of it would set the genre alone.
    @Test
    void synthesized_oneWindowThatIsNotTheWholeStory_isEmptyAndUncertain() {
        final Text decided = BriefDecision.synthesized(
                BriefField.GENRE,
                synthesis("genre", false, "1", "Mystery"),
                UNKNOWN,
                windows(says(BriefField.GENRE, "a slow village mystery", Q1)),
                false);

        assertThat(decided.value()).isNull();
        assertThat(decided.evidence().isKept()).isFalse();
    }

    // IF a cited window counted whatever it read, THEN a "Thriller" could stand on two windows that read "fantasy"
    // and "romance".
    @Test
    void synthesized_citedWindowsThatReadAnotherGenre_doNotSupportTheValue() {
        final Text decided = BriefDecision.synthesized(
                BriefField.GENRE,
                synthesis("genre", false, "1,2", "Thriller"),
                UNKNOWN,
                windows(says(BriefField.GENRE, "fantasy", Q1), says(BriefField.GENRE, "romance", Q2), nothing()),
                false);

        assertThat(decided.value()).isNull();
        assertThat(decided.evidence().isKept()).isFalse();
        assertThat(decided.evidence().agreeing()).isZero();
    }

    @Test
    void synthesized_oneCitedWindowAgreesAndOneDoesNot_countsOnlyTheOneThatAgrees() {
        final Text decided = BriefDecision.synthesized(
                BriefField.GENRE,
                synthesis("genre", false, "1,2", "Mystery"),
                KNOWS_A_MYSTERY,
                windows(says(BriefField.GENRE, "a village mystery", Q1), says(BriefField.GENRE, "romance", Q2)),
                false);

        assertThat(decided.value()).isEqualTo("Mystery");
        assertThat(decided.evidence().basis()).isEqualTo(Basis.KNOWLEDGE);
        assertThat(decided.evidence().quotes()).containsExactly(Q1);
    }

    // IF a window with no reading of the field counted, THEN the model could cite any window it was shown.
    @Test
    void synthesized_citedWindowThatHadNoReading_doesNotCount() {
        final Text decided = BriefDecision.synthesized(
                BriefField.GENRE,
                synthesis("genre", false, "1,4", "Mystery"),
                UNKNOWN,
                readings(BriefField.GENRE, "a slow village mystery"),
                false);

        assertThat(decided.value()).isNull();
        assertThat(decided.evidence().agreeing()).isEqualTo(1);
    }

    @Test
    void synthesized_sameWindowTwiceAndAWindowThatDoesNotExist_countOnce() {
        final Text decided = BriefDecision.synthesized(
                BriefField.GENRE,
                synthesis("genre", false, "2,2,9", "Mystery"),
                UNKNOWN,
                readings(BriefField.GENRE, "a slow village mystery"),
                false);

        assertThat(decided.value()).isNull();
        assertThat(decided.evidence().agreeing()).isEqualTo(1);
    }

    // IF a genre outside the forty were kept, THEN the Book Brief could not show it in Ukrainian.
    @Test
    void synthesized_genreOutsideTheForty_isRejectedWhateverItsBasis() {
        final Text decided = BriefDecision.synthesized(
                BriefField.GENRE,
                synthesis("genre", false, "1,2,3", "Cyberpunk saga"),
                UNKNOWN,
                readings(BriefField.GENRE, "cyberpunk"),
                false);

        assertThat(decided.value()).isNull();
        assertThat(decided.evidence().isKept()).isFalse();
    }

    @Test
    void synthesized_genreInAnotherCase_isTheListedEnglishName() {
        final Text decided = BriefDecision.synthesized(
                BriefField.GENRE,
                synthesis("genre", false, "1,2", "gothic NOVEL"),
                UNKNOWN,
                readings(BriefField.GENRE, "a gothic tale"),
                false);

        assertThat(decided.value()).isEqualTo("Gothic novel");
    }

    @ParameterizedTest
    @CsvSource({"adult, adult", "Young Adult, young adult", "children, children", "general, general"})
    void synthesized_audienceOfTheFive_isThatAudience(final String value, final String expected) {
        final Text decided = BriefDecision.synthesized(
                BriefField.AUDIENCE,
                synthesis("audience", false, "1,2", value),
                UNKNOWN,
                readings(BriefField.AUDIENCE, expected),
                false);

        assertThat(decided.value()).isEqualTo(expected);
    }

    @Test
    void synthesized_academicAudience_isAcademic() {
        final Text decided = BriefDecision.synthesized(
                BriefField.AUDIENCE,
                synthesis("audience", false, "1,2,3", "academic"),
                UNKNOWN,
                readings(BriefField.AUDIENCE, "scholars"),
                false);

        assertThat(decided.value()).isEqualTo("academic");
    }

    @ParameterizedTest
    @ValueSource(strings = {"teenagers", "adults and teens", ""})
    void synthesized_audienceOutsideTheFive_isEmptyAndUncertain(final String value) {
        final Text decided = BriefDecision.synthesized(
                BriefField.AUDIENCE,
                synthesis("audience", false, "1,2,3", value),
                UNKNOWN,
                readings(BriefField.AUDIENCE, "grown readers"),
                false);

        assertThat(decided.value()).isNull();
        assertThat(decided.evidence().isKept()).isFalse();
    }

    @Test
    void synthesized_voiceOfTwelveWords_stands() {
        final String twelve = "a dry formal voice of a late nineteenth century clerk writing letters";

        final Text decided = BriefDecision.synthesized(
                BriefField.VOICE,
                synthesis("voice", false, "1,2", twelve),
                UNKNOWN,
                readings(BriefField.VOICE, "dry and formal"),
                false);

        assertThat(decided.value()).isEqualTo(twelve);
    }

    // IF a voice note of any length were kept, THEN every later prompt would carry a paragraph of the model's prose.
    @Test
    void synthesized_voiceOfThirteenWords_isEmptyAndUncertain() {
        final Text decided = BriefDecision.synthesized(
                BriefField.VOICE,
                synthesis(
                        "voice",
                        false,
                        "1,2",
                        "a dry formal voice of a late nineteenth century clerk writing long letters"),
                UNKNOWN,
                readings(BriefField.VOICE, "dry and formal"),
                false);

        assertThat(decided.value()).isNull();
        assertThat(decided.evidence().isKept()).isFalse();
    }

    // The synthesis call failed or its reply could not be read: nothing is decided, and the screen says uncertain.
    @Test
    void synthesized_noReply_isEmptyAndUncertain() {
        final Text decided = BriefDecision.synthesized(
                BriefField.GENRE, null, KNOWS_A_MYSTERY, readings(BriefField.GENRE, "a slow village mystery"), false);

        assertThat(decided.value()).isNull();
        assertThat(decided.evidence().isKept()).isFalse();
    }
}
