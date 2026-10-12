package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The long-paragraph set of 15i.X3 holds twenty public-domain paragraphs, each over 180 words and each credited. */
class LongParagraphsTest {

    private static final int LONG_PARAGRAPH_WORDS = 180;

    private final List<LongParagraph> paragraphs = LongParagraph.load();

    @Test
    void load_theSet_holdsTwentyParagraphsWithUniqueIds() {
        assertThat(paragraphs).hasSize(20);
        assertThat(paragraphs.stream().map(LongParagraph::id)).doesNotHaveDuplicates();
        assertThat(paragraphs.getFirst().id()).isEqualTo("lp-01");
        assertThat(paragraphs.getLast().id()).isEqualTo("lp-20");
    }

    // IF a paragraph were shorter than the 180-word line, THEN the experiment would measure a paragraph the run
    // drafts whole anyway.
    @Test
    void load_everyParagraph_hasMoreThanOneHundredEightyWordsAndItsCountIsRight() {
        assertThat(paragraphs).allSatisfy(paragraph -> {
            assertThat(paragraph.text().strip().split("\\s+")).hasSizeGreaterThan(LONG_PARAGRAPH_WORDS);
            assertThat(paragraph.words()).isEqualTo(paragraph.text().strip().split("\\s+").length);
        });
    }

    @Test
    void load_everyParagraph_namesItsNovelAuthorAndChapter() {
        assertThat(paragraphs).allSatisfy(paragraph -> {
            assertThat(paragraph.title()).isNotBlank();
            assertThat(paragraph.author()).isNotBlank();
            assertThat(paragraph.chapter()).isNotBlank();
            assertThat(paragraph.gutenberg()).isPositive();
        });
    }

    @Test
    void load_theSet_comesFromFivePublicDomainAuthors() {
        assertThat(paragraphs.stream().map(LongParagraph::author).distinct())
                .containsExactlyInAnyOrder(
                        "Mark Twain", "Jane Austen", "Charles Dickens", "Arthur Conan Doyle", "Robert Louis Stevenson");
        assertThat(paragraphs.stream().map(LongParagraph::title).distinct())
                .containsExactlyInAnyOrder(
                        "The Adventures of Tom Sawyer",
                        "Pride and Prejudice",
                        "Great Expectations",
                        "The Hound of the Baskervilles",
                        "The Adventures of Sherlock Holmes",
                        "Treasure Island");
    }

    @Test
    void load_everyParagraph_isPlainTextWithNoGutenbergMarkup() {
        assertThat(paragraphs)
                .allSatisfy(paragraph -> assertThat(paragraph.text())
                        .doesNotContain("_", "\n", "[", "  ")
                        .isEqualTo(paragraph.text().strip()));
    }
}
