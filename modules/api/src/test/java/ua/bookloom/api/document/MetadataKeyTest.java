package ua.bookloom.api.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The metadata key names the readers write and the import screen and the brief read, pinned to the literals the
 * readers use.
 */
class MetadataKeyTest {

    // IF a constant's wire string drifted from the literal the readers have always written, THEN the import screen
    // would silently lose the title or the author row of every EPUB and FB2 book.
    @ParameterizedTest
    @CsvSource({"TITLE, title", "AUTHOR, author", "DATE, date", "PUBLISHER, publisher"})
    void key_eachConstant_isTheLiteralTheReadersWrite(final MetadataKey constant, final String literal) {
        assertThat(constant.key()).isEqualTo(literal);
    }

    // IF a fifth key were added without a decision, THEN the screen and the brief would carry a fact nobody specified.
    @Test
    void values_declared_areExactlyTitleAuthorDateThenPublisher() {
        assertThat(MetadataKey.values())
                .containsExactly(MetadataKey.TITLE, MetadataKey.AUTHOR, MetadataKey.DATE, MetadataKey.PUBLISHER);
    }
}
