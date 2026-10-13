package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.pipeline.setup.Knowledge.Reason;

/**
 * The verdict on what the model says it knows of a book (15i.B2): the answer stands only when the model says it knows
 * the book, is at least 70 % sure, echoes the metadata's title and author (case, diacritics, punctuation and a leading
 * "the", "a" or "an" of the title do not count; at most 20 % of the longer normalised text may differ) and gives a year
 * no later than five after the metadata year (an edition's date is later than the first publication). Every other answer is "unknown" with one named reason. An invented book stands
 * for the metadata throughout; no real book is named.
 */
class BookKnowledgeVerdictTest {

    private static final String TITLE = "The Lantern Ledger";
    private static final String AUTHOR = "Orsolya Venn";
    private static final int YEAR = 2014;
    private static final String STEM = "o_venn-the_lantern_ledger-2014";

    private static BookFacts facts(
            final @Nullable String title, final @Nullable String author, final @Nullable Integer year) {
        return new BookFacts(title, author, year, null, null);
    }

    private static BookFacts book() {
        return facts(TITLE, AUTHOR, YEAR);
    }

    private static BookKnowledgeReply reply(
            final boolean known,
            final @Nullable String title,
            final @Nullable String author,
            final @Nullable Integer year,
            final double confidence) {
        return new BookKnowledgeReply(
                known,
                title,
                author,
                year,
                "fantasy novel",
                "adult",
                "late twentieth century",
                "third person",
                "A clerk finds a ledger that lists lanterns not yet lit.",
                confidence);
    }

    private static BookKnowledgeReply agreeing() {
        return reply(true, TITLE, AUTHOR, YEAR, 0.9);
    }

    private static String label(final Knowledge knowledge) {
        return knowledge.reason() == null ? "stands" : knowledge.reason().label();
    }

    // IF a matching answer were not kept whole, THEN the brief could not use the genre, audience, era and narration.
    @Test
    void judge_everythingAgrees_standsWithTheAnswersFields() {
        final BookKnowledgeReply answer = agreeing();

        final Knowledge knowledge = BookKnowledgeVerdict.judge(book(), STEM, answer);

        assertThat(knowledge.isKnown()).isTrue();
        assertThat(knowledge.reason()).isNull();
        assertThat(knowledge.reply()).isEqualTo(answer);
        assertThat(knowledge.reply().genre()).isEqualTo("fantasy novel");
        assertThat(knowledge.reply().narration()).isEqualTo("third person");
    }

    // IF a book without a title were asked about, THEN the model would be asked about nothing and could still say yes.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void judge_noTitle_isUnknownWhateverTheModelSaid(final @Nullable String title) {
        final Knowledge knowledge = BookKnowledgeVerdict.judge(facts(title, AUTHOR, YEAR), STEM, agreeing());

        assertThat(knowledge.isKnown()).isFalse();
        assertThat(knowledge.reply()).isNull();
        assertThat(knowledge.reason()).isEqualTo(Reason.NO_TITLE);
        assertThat(knowledge.reason().label()).isEqualTo("no-title");
    }

    // A title that is only the file's name was made up from the file and names no book; the same normalisation as the
    // title check is used, so case, underscores, hyphens, diacritics and a leading article do not hide it.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "The Lantern Ledger|The Lantern Ledger",
                "The Lantern Ledger|the_lantern_ledger",
                "The Lantern Ledger|THE-LANTERN-LEDGER",
                "The Lantern Ledger|lantern_ledger",
                "Café Noir|cafe_noir",
                "Часовщик из Верхних Ключей|часовщик_из_верхних_ключей"
            })
    void judge_titleIsTheFileStem_isUnknownAndNeverReadsTheAnswer(final String title, final String stem) {
        assertThat(label(BookKnowledgeVerdict.judge(facts(title, AUTHOR, YEAR), stem, agreeing())))
                .isEqualTo("title-is-file-stem");
        assertThat(label(BookKnowledgeVerdict.judge(facts(title, AUTHOR, YEAR), stem, null)))
                .isEqualTo("title-is-file-stem");
    }

    // The stem of an ordinary "author-title-year" file name is longer than the title, so the title is not the stem.
    @Test
    void judge_stemHoldsMoreThanTheTitle_isNotTheFileStem() {
        assertThat(BookKnowledgeVerdict.judge(book(), STEM, agreeing()).isKnown())
                .isTrue();
    }

    @Test
    void judge_noTitle_winsOverUnreadableReply() {
        assertThat(label(BookKnowledgeVerdict.judge(facts(null, AUTHOR, YEAR), STEM, null)))
                .isEqualTo("no-title");
    }

    @Test
    void judge_replyCouldNotBeRead_isUnparsable() {
        assertThat(label(BookKnowledgeVerdict.judge(book(), STEM, null))).isEqualTo("unparsable");
    }

    // IF "known: false" were read as a weak yes, THEN an invented book would get a made-up genre and summary.
    @ParameterizedTest
    @ValueSource(doubles = {0.95, 0.1})
    void judge_modelSaysItDoesNotKnowTheBook_isNotKnownWhateverItsConfidenceAndEcho(final double confidence) {
        assertThat(label(BookKnowledgeVerdict.judge(book(), STEM, reply(false, TITLE, AUTHOR, YEAR, confidence))))
                .isEqualTo("not-known");
        assertThat(label(BookKnowledgeVerdict.judge(book(), STEM, reply(false, "", "", 0, confidence))))
                .isEqualTo("not-known");
    }

    // The threshold is 0.7: below it the answer is a guess, at it the answer stands.
    @ParameterizedTest
    @CsvSource({
        "0.0,low-confidence",
        "0.5,low-confidence",
        "0.69,low-confidence",
        "0.699,low-confidence",
        "0.7,stands",
        "0.70,stands",
        "0.71,stands",
        "1.0,stands"
    })
    void judge_confidence_standsFromSevenTenths(final double confidence, final String expected) {
        assertThat(label(BookKnowledgeVerdict.judge(book(), STEM, reply(true, TITLE, AUTHOR, YEAR, confidence))))
                .isEqualTo(expected);
    }

    // IF a hesitant answer about another book were judged by its echo first, THEN the log would blame the echo.
    @Test
    void judge_lowConfidenceAndWrongEcho_isLowConfidence() {
        assertThat(label(BookKnowledgeVerdict.judge(book(), STEM, reply(true, "Another Book", "Anyone", 1900, 0.5))))
                .isEqualTo("low-confidence");
    }

    // Normalisation: case, diacritics, punctuation and a leading article of the title do not count. The boundary rows
    // use a ten-letter text (2 of 10 = 20 % stands, 3 of 10 = 30 % does not), a fifteen-letter text (3 of 15 = exactly
    // 20 % stands) and a fourteen-letter text (3 of 14 = 21.4 % does not), a longer echo (3 inserted letters on 12 is
    // 3 of 15 = 20 %, on 11 it is 3 of 14), and the share is taken of the LONGER normalised text. A title of two words
    // or more found whole at the start of the other (a store's series brackets, ", Complete") also stands; one word, or
    // a prefix that ends inside a word, does not.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                // field|metadata|echo|expected
                "title|The Lantern Ledger|The Lantern Ledger|stands",
                "title|The Lantern Ledger|THE LANTERN LEDGER|stands",
                "title|The Lantern Ledger|the lantern ledger.|stands",
                "title|The Lantern Ledger|Lantern Ledger|stands",
                "title|Lantern Ledger|The Lantern Ledger|stands",
                "title|A Winter of Small Clocks|Winter of Small Clocks|stands",
                "title|An Orchard of Glass|Orchard of Glass|stands",
                "title|Café Noir|CAFE NOIR|stands",
                "title|Cafe Noir|Café Noir!|stands",
                "title|Notes from a Borrowed Garden|Notes from a Borrowed Garden|stands",
                "title|Dr. Quill & Mr. Ash|Dr Quill Mr Ash|stands",
                "title|Salt-Road to Kellmoor|Salt Road to Kellmoor|stands",
                "title|Часовщик из Верхних Ключей|ЧАСОВЩИК ИЗ ВЕРХНИХ КЛЮЧЕЙ|stands",
                "title|abcdefghij|abcdefghXY|stands",
                "title|abcdefghij|abcdefgXYZ|title-mismatch",
                "title|abcdefghijklmno|abcdefghijklXYZ|stands",
                "title|abcdefghijklmn|abcdefghijkXYZ|title-mismatch",
                "title|abcdefghijkl|abcdefghijklXYZ|stands",
                "title|abcdefghijk|abcdefghijkXYZ|title-mismatch",
                "title|The Lantern Ledger|The Lantern Ledger: A Novel|stands",
                "title|Salt-Road to Kellmoor|Salt Road to Kellmoor; or, The Long Way Round|stands",
                "title|Salt Road to Kellmoor: A Novel|Salt Road to Kellmoor|stands",
                "title|The Lantern Ledger: A Novel|The Copper Ledger: A Novel|title-mismatch",
                "title|;Lantern|Lantern Ledger|title-mismatch",
                "title|The Lantern Ledger|Moby Dick|title-mismatch",
                "title|The Lantern Ledger|The Copper Ledger of Ash|title-mismatch",
                "title|The Lantern Ledger (Ledger Cycle Book 1) (The Ledger Cycle)|The Lantern Ledger|stands",
                "title|The Lantern Ledger, Complete|The Lantern Ledger|stands",
                "title|The Lantern Ledger|The Lantern Ledger (Ledger Cycle, Volume One)|stands",
                "title|Lantern Ledger and Other Tales of the Northern Marches|Lantern Ledger|stands",
                "title|Ledger (Ledger Cycle Book 1) (The Ledger Cycle)|Ledger|title-mismatch",
                "title|The Lantern (Ledger Cycle Book 1) (The Ledger Cycle)|The Lantern|title-mismatch",
                "title|Lantern Led (Ledger Cycle Book 1) (The Ledger Cycle)|Lantern Ledger|title-mismatch",
                "author|Orsolya Venn|orsolya venn|stands",
                "author|Orsolya Venn|Orsolya Venn.|stands",
                "author|Zoë Brandt-Åske|Zoe Brandt Aske|stands",
                "author|Idris Calloway|Idris Calloway|stands",
                "author|abcdefghij|abcdefghXY|stands",
                "author|abcdefghij|abcdefgXYZ|author-mismatch",
                "author|abcdefghijklmno|abcdefghijklXYZ|stands",
                "author|abcdefghijklmn|abcdefghijkXYZ|author-mismatch",
                "author|Orsolya Venn|Fenna Aldous|author-mismatch",
                "author|Orsolya Venn|Venn, Orsolya|stands",
                "author|Venn, Orsolya|Orsolya Venn|stands",
                "author|Orsolya Venn|Venn, Fenna|author-mismatch"
            })
    void judge_echo_matchesTheMetadataWithinAFifthOfTheLongerText(
            final String field, final String metadata, final String echo, final String expected) {
        final boolean title = field.equals("title");
        final BookFacts facts = facts(title ? metadata : TITLE, title ? AUTHOR : metadata, YEAR);
        final BookKnowledgeReply answer = reply(true, title ? echo : TITLE, title ? AUTHOR : echo, YEAR, 0.9);

        assertThat(label(BookKnowledgeVerdict.judge(facts, "unrelated-file-name", answer)))
                .isEqualTo(expected);
    }

    // A model that echoes nothing has not named the book, whatever else it said.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void judge_echoedTitleMissing_isTitleMismatch(final @Nullable String echoed) {
        assertThat(label(BookKnowledgeVerdict.judge(book(), STEM, reply(true, echoed, AUTHOR, YEAR, 0.9))))
                .isEqualTo("title-mismatch");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void judge_echoedAuthorMissingWhileTheBookHasOne_isAuthorMismatch(final @Nullable String echoed) {
        assertThat(label(BookKnowledgeVerdict.judge(book(), STEM, reply(true, TITLE, echoed, YEAR, 0.9))))
                .isEqualTo("author-mismatch");
    }

    // Nothing to compare an author with: the title alone has to match.
    @ParameterizedTest
    @ValueSource(strings = {"Anyone At All", ""})
    void judge_bookHasNoAuthor_authorIsNotChecked(final String echoed) {
        final Knowledge knowledge =
                BookKnowledgeVerdict.judge(facts(TITLE, null, YEAR), STEM, reply(true, TITLE, echoed, YEAR, 0.9));

        assertThat(knowledge.isKnown()).isTrue();
    }

    @Test
    void judge_titleAuthorAndYearAllWrong_namesTheTitleFirst() {
        assertThat(label(BookKnowledgeVerdict.judge(book(), STEM, reply(true, "Other", "Someone Else", 1800, 0.9))))
                .isEqualTo("title-mismatch");
    }

    @Test
    void judge_authorAndYearWrong_namesTheAuthorBeforeTheYear() {
        assertThat(label(BookKnowledgeVerdict.judge(book(), STEM, reply(true, TITLE, "Someone Else", 1800, 0.9))))
                .isEqualTo("author-mismatch");
    }

    // The metadata's date is often an edition's or a release's, so an earlier year (the first publication) stands;
    // the answer may be at most five years after it: 2019 stands, 2020 does not.
    @ParameterizedTest
    @CsvSource({
        "2014,stands",
        "2013,stands",
        "2015,stands",
        "2019,stands",
        "2009,stands",
        "2008,stands",
        "1923,stands",
        "2020,year-mismatch",
        "2031,year-mismatch",
        "0,year-mismatch"
    })
    void judge_year_standsWhenNoLaterThanFiveAfterTheMetadataYear(final int echoed, final String expected) {
        assertThat(label(BookKnowledgeVerdict.judge(book(), STEM, reply(true, TITLE, AUTHOR, echoed, 0.9))))
                .isEqualTo(expected);
    }

    // A book whose metadata gives a year but whose answer gives none cannot be checked, so it does not stand.
    @Test
    void judge_metadataHasYearButAnswerHasNone_isYearMismatch() {
        assertThat(label(BookKnowledgeVerdict.judge(book(), STEM, reply(true, TITLE, AUTHOR, null, 0.9))))
                .isEqualTo("year-mismatch");
    }

    // No metadata year, nothing to compare: the year is not checked, whatever the answer gives or leaves out.
    @ParameterizedTest
    @CsvSource(
            value = {"1850", "2030", "0", "NULL"},
            nullValues = "NULL")
    void judge_metadataHasNoYear_yearIsNotChecked(final @Nullable Integer echoed) {
        final Knowledge knowledge =
                BookKnowledgeVerdict.judge(facts(TITLE, AUTHOR, null), STEM, reply(true, TITLE, AUTHOR, echoed, 0.9));

        assertThat(knowledge.isKnown()).isTrue();
        assertThat(knowledge.reply().year()).isEqualTo(echoed);
    }

    @Test
    void reasonLabels_allNine_areTheLoggedWords() {
        assertThat(Reason.values())
                .extracting(Reason::label)
                .containsExactly(
                        "no-title",
                        "title-is-file-stem",
                        "not-known",
                        "low-confidence",
                        "title-mismatch",
                        "author-mismatch",
                        "year-mismatch",
                        "unparsable",
                        "call-failed");
    }
}
