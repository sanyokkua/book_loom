package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The four reason lines under the original title read the same wherever they are shown, in both languages. */
class ExportTitleReasonMessagesTest {

    // IF a reason key were missing or worded differently, THEN the card would show a raw key or a wrong source.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "export.bookTitle.fromDetails|From the book's details|З відомостей про книжку",
                "export.bookTitle.fromFrontMatter|From the front matter|Із вступного блоку метаданих",
                "export.bookTitle.fromHeading|From the first heading|З першого заголовка",
                "export.bookTitle.fromFileName|The book names no title — the file name is used|Книжка не називає назви — використано назву файлу"
            })
    void reasonKey_inBothCatalogues_hasTheAgreedText(final String key, final String english, final String ukrainian) {
        assertThat(Catalogues.load("en")).containsEntry(key, english);
        assertThat(Catalogues.load("uk")).containsEntry(key, ukrainian);
    }
}
