package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExportPathRulesTest {

    @ParameterizedTest
    @CsvSource({
        "/books/Kobzar.fb2.zip, /books/Kobzar.fb2.zip, SOURCE_ITSELF",
        "/books/Kobzar.fb2.zip, /books/../books/Kobzar.fb2.zip, SOURCE_ITSELF",
        "/books/Kobzar.fb2.zip, /books/Kobzar.uk.fb2, CHANGED_TYPE",
        "/books/Book.md, /books/Book.uk.epub, CHANGED_TYPE",
        "/books/Book.md, /books/Book.uk.pdf, CHANGED_TYPE"
    })
    void refusal_destinationTheBookCannotBeWrittenTo_isNamed(
            final String source, final String destination, final ExportPathRules.Refusal expected) {
        assertThat(ExportPathRules.refusal(Path.of(source), Path.of(destination)))
                .contains(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "/books/Book.md, /books/Book.uk.markdown",
        "/books/Kobzar.fb2.zip, /out/Kobzar.uk.fb2.zip",
        "/books/Frankenstein.epub, /books/Frankenstein.uk.epub"
    })
    void refusal_sameTypeElsewhere_isNone(final String source, final String destination) {
        assertThat(ExportPathRules.refusal(Path.of(source), Path.of(destination)))
                .isEmpty();
    }
}
