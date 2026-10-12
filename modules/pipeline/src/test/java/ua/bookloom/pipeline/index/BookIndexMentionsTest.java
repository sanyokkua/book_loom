package ua.bookloom.pipeline.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Mentions of names: runs of up to three words separated by blanks only, a possessive ending a run, letter case read
 * on request, and each mention told to be narration or speech. A mention's range is in the segment's text with each
 * placeholder token read as one space and excludes a possessive ending.
 */
class BookIndexMentionsTest {

    private static BookIndex index(final String... lines) {
        return BookIndex.of(IndexTestBooks.oneUnit(lines));
    }

    private static String textOf(final String line, final Mention mention) {
        return line.substring(mention.start(), mention.end());
    }

    // IF a name is also an ordinary word (Mark / a mark), THEN case-sensitive reading finds only the capitalised one.
    @Test
    void mentions_sensitive_findsTheCapitalisedNameNotTheCommonWord() {
        final BookIndex index = statsBook();

        assertThat(index.mentions(List.of("Mark"), Case.SENSITIVE))
                .extracting(Mention::ordinal, Mention::start, Mention::end)
                .containsExactly(tuple(3, 8, 12), tuple(5, 8, 12));
    }

    @Test
    void mentions_insensitive_findsEveryWritingOfTheWord() {
        final BookIndex index = statsBook();

        assertThat(index.mentions(List.of("Mark"), Case.INSENSITIVE))
                .extracting(Mention::ordinal, Mention::start)
                .containsExactly(tuple(1, 18), tuple(1, 26), tuple(3, 8), tuple(5, 8));
    }

    @Test
    void mentions_aNameAndItsSentence_carryTheNameAsGivenAndTheSentenceIndex() {
        final BookIndex index = statsBook();

        assertThat(index.mentions(List.of("mark"), Case.INSENSITIVE))
                .extracting(Mention::name, Mention::sentence)
                .containsExactly(tuple("mark", 0), tuple("mark", 1), tuple("mark", 0), tuple("mark", 0));
    }

    // IF a name has two words, THEN its mention starts at the first word and ends after the last.
    @Test
    void mentions_aTwoWordName_spansBothWords() {
        final BookIndex index = statsBook();

        assertThat(index.mentions(List.of("Great Hall"), Case.SENSITIVE))
                .extracting(Mention::ordinal, Mention::start, Mention::end)
                .containsExactly(tuple(5, 24, 34));
    }

    // IF a name has three words, THEN it is found; a name of four words is never a run, so it has no mention.
    @Test
    void mentions_aThreeWordNameIsFound_aFourWordNameIsNot() {
        final BookIndex index = statsBook();

        assertThat(index.mentions(List.of("Ivo and Mark"), Case.SENSITIVE))
                .extracting(Mention::ordinal, Mention::start, Mention::end)
                .containsExactly(tuple(5, 0, 12));
        assertThat(index.mentions(List.of("Ivo and Mark met"), Case.SENSITIVE)).isEmpty();
    }

    // IF a possessive ends the word (15h.A6), THEN "Ashdown’s" is a mention of "Ashdown" and the range stops before it.
    @Test
    void mentions_aPossessiveForm_isAMentionOfTheBaseNameWithoutTheEnding() {
        final String line = "He looked at Ashdown’s door.";

        final List<Mention> found = index(line).mentions(List.of("Ashdown"), Case.SENSITIVE);

        assertThat(found)
                .singleElement()
                .satisfies(mention -> assertThat(textOf(line, mention)).isEqualTo("Ashdown"));
    }

    @Test
    void mentions_aTwoWordNameWithAPossessiveOnItsLastWord_isFoundWithoutTheEnding() {
        final String line = "He opened Garrick Ashdown’s door.";

        final List<Mention> found = index(line).mentions(List.of("Garrick Ashdown"), Case.SENSITIVE);

        assertThat(found)
                .singleElement()
                .satisfies(mention -> assertThat(textOf(line, mention)).isEqualTo("Garrick Ashdown"));
    }

    // IF a possessive sits on a non-last word, THEN it ends the run and the two words are two names, not one.
    @Test
    void mentions_aPossessiveInsideARun_endsTheRun() {
        assertThat(index("We met Ashdown’s Garrick there.").mentions(List.of("Ashdown Garrick"), Case.INSENSITIVE))
                .isEmpty();
    }

    // IF a comma or full stop lies between two words, THEN they are not one run.
    @ParameterizedTest
    @MethodSource("brokenRuns")
    void mentions_wordsNotSeparatedByBlanksOnly_areNoMentionOfTheJoinedName(final String line) {
        assertThat(index(line).mentions(List.of("Hale Ann"), Case.INSENSITIVE)).isEmpty();
    }

    static Stream<Arguments> brokenRuns() {
        return Stream.of(
                Arguments.of("Then Hale, Ann sang."),
                Arguments.of("Then Hale. Ann sang."),
                Arguments.of("Then Hale and Ann sang."));
    }

    // IF a placeholder token joins two words, THEN it reads as one space and the words are a run (Hale Street).
    @Test
    void mentions_aPlaceholderTokenBetweenWords_readsAsASpace() {
        assertThat(index("Meet Hale⟦g1⟧Street now.").mentions(List.of("Hale Street"), Case.SENSITIVE))
                .extracting(Mention::start, Mention::end)
                .containsExactly(tuple(5, 16));
    }

    // IF two names are asked for, THEN their mentions come together in reading order, each carrying its own name.
    @Test
    void mentions_twoNames_areMergedInReadingOrder() {
        final BookIndex index = index("Ann met Bea.", "Bea left. Ann stayed.");

        assertThat(index.mentions(List.of("Ann", "Bea"), Case.SENSITIVE))
                .extracting(Mention::name, Mention::ordinal, Mention::start)
                .containsExactly(tuple("Ann", 0, 0), tuple("Bea", 0, 8), tuple("Bea", 1, 0), tuple("Ann", 1, 10));
    }

    // IF one name is part of another, THEN each name still lists its own mentions.
    @Test
    void mentions_aNameInsideAnotherName_isListedForBoth() {
        assertThat(index("Garrick Ashdown smiled.").mentions(List.of("Ashdown", "Garrick Ashdown"), Case.SENSITIVE))
                .extracting(Mention::name)
                .containsExactlyInAnyOrder("Ashdown", "Garrick Ashdown");
    }

    @Test
    void mentions_noNamesOrNoMatch_isEmpty() {
        final BookIndex index = statsBook();

        assertThat(index.mentions(List.of(), Case.SENSITIVE)).isEmpty();
        assertThat(index.mentions(List.of("Zed"), Case.INSENSITIVE)).isEmpty();
    }

    // IF a mention lies inside quotation marks, or in a dash-led line before the second dash, THEN it is speech;
    // otherwise it is narration.
    @ParameterizedTest
    @MethodSource("speechAndNarration")
    void mentions_quotesAndDashes_decideNarration(final String line, final boolean narration) {
        assertThat(index(line).mentions(List.of("Ivo"), Case.SENSITIVE))
                .singleElement()
                .extracting(Mention::narration)
                .isEqualTo(narration);
    }

    static Stream<Arguments> speechAndNarration() {
        return Stream.of(
                Arguments.of("She asked where Ivo was.", true),
                Arguments.of("“Where is Ivo?” she asked.", false),
                Arguments.of("\"Where is Ivo?\" she asked.", false),
                Arguments.of("«Where is Ivo?» she asked.", false),
                Arguments.of("She said, “Come,” and Ivo came.", true),
                Arguments.of("— Ivo, come here, — said Ann.", false),
                Arguments.of("— Come here, — said Ann to Ivo.", true));
    }

    static BookIndex statsBook() {
        return BookIndex.of(BookIndexStatsTest.book());
    }
}
