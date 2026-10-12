package ua.bookloom.pipeline.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.UnitRole;

/**
 * What the index records of a small invented book: the reading order (ordinals count every segment, the auxiliary
 * unit included, in the order the units are listed), each word's postings and the counts a name scan reads.
 *
 * <p>Ordinals of {@link #book()}: front 0; ch1 1, 2, 3; aux 4; ch2 5; back 6.
 */
class BookIndexStatsTest {

    private static final double EXACT = 1e-9;

    static Document book() {
        return IndexTestBooks.sample();
    }

    // IF a name is written with a capital away from a sentence start every time, THEN none of its uses is lower case
    // and the three sentence-initial uses are told apart from the three mid-sentence ones.
    @Test
    void stats_aName_countsCapitalisedMidSentenceInitialAndStandaloneUses() {
        final WordStats stats = BookIndex.of(book()).stats("Ivo");

        assertThat(stats.total()).isEqualTo(6);
        assertThat(stats.lowerCase()).isZero();
        assertThat(stats.capitalisedMidSentence()).isEqualTo(3);
        assertThat(stats.sentenceInitial()).isEqualTo(3);
        assertThat(stats.standalone()).isEqualTo(3);
        assertThat(stats.lowerShare()).isZero();
        assertThat(stats.segments()).isEqualTo(6);
        assertThat(stats.firstOrdinal()).isEqualTo(1);
    }

    // IF a word is written both ways, THEN lowerShare is the lower-case uses over lower case plus capitalised.
    @Test
    void stats_aWordWrittenBothWays_hasALowerShareOfLowerOverBoth() {
        final WordStats mark = BookIndex.of(book()).stats("mark");
        final WordStats bridge = BookIndex.of(book()).stats("bridge");

        assertThat(mark.total()).isEqualTo(4);
        assertThat(mark.lowerCase()).isEqualTo(2);
        assertThat(mark.capitalisedMidSentence()).isEqualTo(2);
        assertThat(mark.sentenceInitial()).isZero();
        assertThat(mark.standalone()).isEqualTo(2);
        assertThat(mark.lowerShare()).isCloseTo(0.5, within(EXACT));
        assertThat(mark.segments()).isEqualTo(3);
        assertThat(mark.firstOrdinal()).isEqualTo(1);
        assertThat(bridge.lowerShare()).isCloseTo(0.5, within(EXACT));
        assertThat(bridge.firstOrdinal()).isEqualTo(2);
    }

    // IF a capital stands beside another capitalised word (Great Hall), THEN it is part of a name and not standalone;
    // a capital that opens a sentence is counted as sentence-initial only.
    @Test
    void stats_aWordInsideACapitalisedPair_isNotStandalone() {
        final WordStats hall = BookIndex.of(book()).stats("hall");

        assertThat(hall.total()).isEqualTo(2);
        assertThat(hall.capitalisedMidSentence()).isEqualTo(1);
        assertThat(hall.sentenceInitial()).isEqualTo(1);
        assertThat(hall.standalone()).isZero();
        assertThat(hall.segments()).isEqualTo(1);
        assertThat(hall.firstOrdinal()).isEqualTo(5);
    }

    @Test
    void stats_aCommonWord_isMostlyLowerCase() {
        final WordStats stats = BookIndex.of(book()).stats("the");

        assertThat(stats.total()).isEqualTo(5);
        assertThat(stats.lowerCase()).isEqualTo(4);
        assertThat(stats.sentenceInitial()).isEqualTo(1);
        assertThat(stats.lowerShare()).isCloseTo(0.8, within(EXACT));
        assertThat(stats.segments()).isEqualTo(3);
        assertThat(stats.firstOrdinal()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("spellingsOfIvo")
    void stats_anyLetterCaseOfTheQuery_isTheSameWord(final String query) {
        assertThat(BookIndex.of(book()).stats(query))
                .isEqualTo(BookIndex.of(book()).stats("ivo"));
    }

    static Stream<Arguments> spellingsOfIvo() {
        return Stream.of(Arguments.of("Ivo"), Arguments.of("IVO"), Arguments.of("iVo"));
    }

    // IF the book never writes the word, THEN every count is 0, the lower share 0 and there is no first ordinal.
    @Test
    void stats_aWordTheBookNeverWrites_isAllZeroWithNoFirstOrdinal() {
        assertThat(BookIndex.of(book()).stats("zzz")).isEqualTo(new WordStats(0, 0, 0, 0, 0, 0.0, 0, -1));
    }

    // IF a name carries a possessive ending (Ivo’s), THEN the use is counted for the name itself.
    @Test
    void stats_aPossessiveUse_countsForTheBaseWord() {
        assertThat(BookIndex.of(book()).postings("ivo"))
                .anySatisfy(posting -> assertThat(posting.ordinal()).isEqualTo(3));
    }

    @Test
    void postings_aWordWrittenTwiceInOneSegment_listsBothWithOffsetAndSentence() {
        assertThat(BookIndex.of(book()).postings("mark"))
                .containsExactly(
                        new Posting(1, "ch1", 18, 0, false, false, false),
                        new Posting(1, "ch1", 26, 1, false, false, false),
                        new Posting(3, "ch1", 8, 0, true, false, true),
                        new Posting(5, "ch2", 8, 0, true, false, true));
    }

    @Test
    void postings_aSentenceInitialCapitalAndAPairedCapital_areNotStandalone() {
        assertThat(BookIndex.of(book()).postings("hall"))
                .containsExactly(
                        new Posting(5, "ch2", 30, 0, true, false, false),
                        new Posting(5, "ch2", 36, 1, true, true, false));
    }

    @Test
    void postings_aWordNeverWritten_isEmpty() {
        assertThat(BookIndex.of(book()).postings("zzz")).isEmpty();
    }

    // IF a placeholder token sits between two words, THEN the offsets count it as one space, like Occurrences.read.
    @Test
    void postings_aPlaceholderToken_countsAsOneSpaceInTheOffsets() {
        final BookIndex index = BookIndex.of(IndexTestBooks.oneUnit("Meet Hale⟦g1⟧Street now."));

        assertThat(index.postings("street")).containsExactly(new Posting(0, "body", 10, 0, true, false, false));
    }

    @Test
    void size_book_countsEverySegmentOfEveryUnit() {
        assertThat(BookIndex.of(book()).size()).isEqualTo(7);
    }

    @Test
    void segment_ordinals_giveUnitRoleKindAndAuxiliaryFlagInListedOrder() {
        final BookIndex index = BookIndex.of(book());

        assertThat(index.segment(0))
                .isEqualTo(new SegmentInfo(0, "front:0", "front", UnitRole.FRONT_MATTER, SegmentKind.TITLE, false));
        assertThat(index.segment(3))
                .isEqualTo(new SegmentInfo(3, "ch1:2", "ch1", UnitRole.BODY, SegmentKind.PARAGRAPH, false));
        assertThat(index.segment(4))
                .isEqualTo(new SegmentInfo(4, "aux:0", "aux", UnitRole.BODY, SegmentKind.METADATA_TITLE, true));
        assertThat(index.segment(6))
                .isEqualTo(new SegmentInfo(6, "back:0", "back", UnitRole.BACK_MATTER, SegmentKind.PARAGRAPH, false));
    }

    @Test
    void ordinalOf_segmentId_givesItsOrdinalAndEmptyForAnUnknownId() {
        final BookIndex index = BookIndex.of(book());

        assertThat(index.ordinalOf("ch2:0")).hasValue(5);
        assertThat(index.ordinalOf("nope:0")).isEmpty();
    }

    // IF a book has front matter, an auxiliary unit, two body units and back matter, THEN story() is the body units'
    // segments only, in listed order - the segments FrequencyScan.storyText reads.
    @Test
    void story_frontBodyAuxAndBackUnits_isTheNonAuxiliaryBodyOrdinals() {
        assertThat(BookIndex.of(book()).story()).containsExactly(1, 2, 3, 5);
    }

    @Test
    void story_aBookWithNoBodyUnit_isEmpty() {
        assertThat(BookIndex.of(IndexTestBooks.document(IndexTestBooks.unit("only", UnitRole.BACK_MATTER, "Thanks.")))
                        .story())
                .isEmpty();
    }

    @Test
    void of_aBookWithNoSegments_hasNoWordsAndNoStory() {
        final BookIndex index = BookIndex.of(IndexTestBooks.document());

        assertThat(index.size()).isZero();
        assertThat(index.story()).isEmpty();
        assertThat(index.stats("ivo").total()).isZero();
    }
}
