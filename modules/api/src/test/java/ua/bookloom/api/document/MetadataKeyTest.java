package ua.bookloom.api.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The two metadata key names the EPUB and FB2 readers write and the import screen reads, pinned to the literals the
 * readers used before they were named.
 */
class MetadataKeyTest {

    // IF a constant's wire string drifted from the literal the readers have always written, THEN the import screen
    // would silently lose the title or the author row of every EPUB and FB2 book.
    @ParameterizedTest
    @CsvSource({"TITLE, title", "AUTHOR, author"})
    void key_eachConstant_isTheLiteralTheReadersWrite(final MetadataKey constant, final String literal) {
        assertThat(constant.key()).isEqualTo(literal);
    }

    // IF a third key were added without a decision, THEN the screen would have a metadata row nobody specified.
    @Test
    void values_declared_areExactlyTitleThenAuthor() {
        assertThat(MetadataKey.values()).containsExactly(MetadataKey.TITLE, MetadataKey.AUTHOR);
    }
}
