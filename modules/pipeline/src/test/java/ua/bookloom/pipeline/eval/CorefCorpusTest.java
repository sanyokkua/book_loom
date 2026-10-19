package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.pipeline.eval.CorefWindow.Referent;

/** The coreference windows (15i.E2) are well formed, so a score means something. */
class CorefCorpusTest {

    private static final int MIN_WINDOWS = 30;
    private static final int MIN_PER_SHAPE = 3;
    private static final Pattern THIRD_PERSON = Pattern.compile(
            "\\b(he|she|him|her|his|hers|himself|herself|they|them|their|theirs)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPEECH = Pattern.compile("“[^”]*”");
    private static final Pattern FIRST_PERSON = Pattern.compile("\\bI\\b");

    static Stream<CorefWindow> windows() {
        return CorefCorpus.windows().stream();
    }

    private static Stream<CorefWindow> shaped(final String shape) {
        return windows().filter(window -> window.shape().equals(shape));
    }

    static Stream<CorefWindow> firstPersonWindows() {
        return shaped("first-person-narrator");
    }

    static Stream<CorefWindow> placeWindows() {
        return shaped("place-people");
    }

    static Stream<CorefWindow> speechWindows() {
        return shaped("speaker-in-quotes");
    }

    static Stream<CorefWindow> noReferentWindows() {
        return shaped("no-referent");
    }

    static Stream<CorefWindow> nextSentenceWindows() {
        return shaped("next-sentence-other");
    }

    private static long occurrences(final String text, final String part) {
        return text.split(Pattern.quote(part), -1).length - 1L;
    }

    @Test
    void windows_fixture_holdsAtLeastThirtyWindowsWithUniqueIds() {
        final List<CorefWindow> all = CorefCorpus.windows();

        assertThat(all).hasSizeGreaterThanOrEqualTo(MIN_WINDOWS);
        assertThat(all).extracting(CorefWindow::id).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @MethodSource("windows")
    void window_text_hasTwoToFiveParagraphsAndNamesItsTarget(final CorefWindow window) {
        assertThat(window.paragraphs()).hasSizeBetween(2, 5);
        assertThat(window.text()).contains(window.target());
    }

    // IF a gold pronoun's quote is not in the text once, THEN a claim could not be matched to it.
    @ParameterizedTest
    @MethodSource("windows")
    void window_goldPronoun_quoteOccursOnceAndHoldsThePronounAsAWord(final CorefWindow window) {
        assertThat(window.pronouns()).allSatisfy(pronoun -> {
            assertThat(occurrences(window.text(), pronoun.quote())).isEqualTo(1);
            assertThat(Pattern.compile("\\b" + Pattern.quote(pronoun.pronoun()) + "\\b")
                            .matcher(pronoun.quote())
                            .find())
                    .isTrue();
        });
    }

    // IF a pronoun of the text is missing from the gold, THEN a vote on it would be scored as a vote on no pronoun.
    @ParameterizedTest
    @MethodSource("windows")
    void window_everyThirdPersonPronounOfTheText_isListedInTheGold(final CorefWindow window) {
        assertThat(THIRD_PERSON.matcher(window.text()).results().count())
                .isEqualTo(window.pronouns().size());
    }

    @ParameterizedTest
    @MethodSource("windows")
    void window_goldPronoun_inSpeechFlagFollowsTheQuoteMarksOfTheText(final CorefWindow window) {
        final List<String> spoken = SPEECH.matcher(window.text())
                .results()
                .map(match -> match.group())
                .toList();

        assertThat(window.pronouns())
                .allSatisfy(pronoun -> assertThat(spoken.stream().anyMatch(span -> span.contains(pronoun.quote())))
                        .isEqualTo(pronoun.inSpeech()));
    }

    @Test
    void windows_shapes_areExactlyTheSixThatAreDocumented() {
        assertThat(windows().map(CorefWindow::shape).distinct())
                .containsExactlyInAnyOrder(
                        "next-sentence-other",
                        "speaker-in-quotes",
                        "place-people",
                        "first-person-narrator",
                        "no-referent",
                        "plain");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "next-sentence-other",
                "speaker-in-quotes",
                "place-people",
                "first-person-narrator",
                "no-referent",
                "plain"
            })
    void windows_eachShape_appearsAtLeastThreeTimes(final String shape) {
        assertThat(shaped(shape).count()).isGreaterThanOrEqualTo(MIN_PER_SHAPE);
    }

    @ParameterizedTest
    @EnumSource(Referent.class)
    void windows_eachReferentKind_isGoldForAtLeastThreePronouns(final Referent referent) {
        assertThat(windows()
                        .flatMap(window -> window.pronouns().stream())
                        .filter(pronoun -> pronoun.referent() == referent)
                        .count())
                .isGreaterThanOrEqualTo(MIN_PER_SHAPE);
    }

    // IF the narrator is the target, THEN no he/she in the text is the target: a vote there is a wrong one.
    @ParameterizedTest
    @MethodSource("firstPersonWindows")
    void firstPersonWindow_isToldInFirstPersonAndHasNoPronounForTheTarget(final CorefWindow window) {
        assertThat(FIRST_PERSON.matcher(window.text()).find()).isTrue();
        assertThat(window.pronouns()).noneMatch(pronoun -> pronoun.referent() == Referent.TARGET);
    }

    @ParameterizedTest
    @MethodSource("placeWindows")
    void placeWindow_hasAPronounForItsPeopleAndNoneForATargetPerson(final CorefWindow window) {
        assertThat(window.pronouns()).anyMatch(pronoun -> pronoun.referent() == Referent.PLACE_PEOPLE);
        assertThat(window.pronouns()).noneMatch(pronoun -> pronoun.referent() == Referent.TARGET);
    }

    @ParameterizedTest
    @MethodSource("speechWindows")
    void speechWindow_hasAPronounInsideQuotedSpeech(final CorefWindow window) {
        assertThat(window.pronouns()).anyMatch(pronoun -> pronoun.inSpeech());
    }

    @ParameterizedTest
    @MethodSource("noReferentWindows")
    void noReferentWindow_pronounsAllHaveNoReferent(final CorefWindow window) {
        assertThat(window.pronouns()).allMatch(pronoun -> pronoun.referent() == Referent.NONE);
    }

    @ParameterizedTest
    @MethodSource("nextSentenceWindows")
    void nextSentenceWindow_hasAPronounOfAnotherCharacter(final CorefWindow window) {
        assertThat(window.pronouns()).anyMatch(pronoun -> pronoun.referent() == Referent.OTHER);
    }
}
