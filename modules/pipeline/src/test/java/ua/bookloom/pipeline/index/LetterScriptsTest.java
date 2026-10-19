package ua.bookloom.pipeline.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.Character.UnicodeScript;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.UnitRole;

/** The script a text, and a book's story, is mostly written in, counted from the letters alone. */
class LetterScriptsTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "He whispered some words|LATIN",
                "Вона прошепотіла кілька слів|CYRILLIC",
                "Mostly Latin with одне слово|LATIN",
                "ひらがなとカタカナと漢字|HAN",
                "λόγος και λόγος|GREEK"
            })
    void majorityOf_text_isTheScriptOfMostLetters(final String text, final UnicodeScript expected) {
        assertThat(LetterScripts.majorityOf(text)).contains(expected);
    }

    // IF a placeholder token's own letter counted, THEN a tokens-only segment would read as Latin.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"⟦g0⟧⟦g1⟧", "1914 — 1918", "''"})
    void majorityOf_textWithNoLetter_isEmpty(final String text) {
        assertThat(LetterScripts.majorityOf(text)).isEqualTo(Optional.empty());
    }

    @Test
    void majorityScript_bookWhoseStoryIsCyrillic_ignoresLatinFrontMatter() {
        final BookIndex index = BookIndex.of(IndexTestBooks.document(
                IndexTestBooks.unit("front", UnitRole.FRONT_MATTER, "Harbour Press presents the whole book here."),
                IndexTestBooks.unit("ch1", UnitRole.BODY, "Вона довго йшла містом.", "Потім сіла на лавку.")));

        assertThat(index.majorityScript()).contains(UnicodeScript.CYRILLIC);
    }

    @Test
    void majorityScript_bookWithNoStoryLetters_isEmpty() {
        final BookIndex index =
                BookIndex.of(IndexTestBooks.document(IndexTestBooks.unit("ch1", UnitRole.BODY, "1914.", "⟦g0⟧")));

        assertThat(index.majorityScript()).isEmpty();
    }
}
