package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.project.Genre;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.Messages;

/**
 * The genres live in {@code :api} since 15i.B4 and know nothing of the interface; the Book Brief shows each by its own
 * catalogue entry, in English and in Ukrainian, while the brief still holds the English name.
 */
class GenreLabelsTest {

    private static Messages messagesFor(final String tag) {
        return new Messages((LocaleProvider) () -> Locale.forLanguageTag(tag));
    }

    @Test
    void keyOf_everyGenre_isItsOwnEntry() {
        assertThat(Arrays.stream(Genre.values()).map(GenreLabels::keyOf))
                .doesNotHaveDuplicates()
                .hasSize(40);
    }

    // IF a genre had no entry of its own, THEN the genre box would show another genre's name for it.
    @ParameterizedTest
    @EnumSource(Genre.class)
    void keyOf_genreInEnglish_isShownByItsEnglishName(final Genre genre) {
        assertThat(messagesFor("en").get(GenreLabels.keyOf(genre))).isEqualTo(genre.english());
    }

    @ParameterizedTest
    @EnumSource(Genre.class)
    void keyOf_genreInUkrainian_isShownInUkrainian(final Genre genre) {
        assertThat(messagesFor("uk").get(GenreLabels.keyOf(genre)))
                .isNotBlank()
                .isNotEqualTo(genre.english())
                .containsPattern("\\p{IsCyrillic}");
    }

    @Test
    void keyOf_gothicNovelUnderUkrainian_isShownInUkrainianAndSentInEnglish() {
        assertThat(messagesFor("uk").get(GenreLabels.keyOf(Genre.GOTHIC_NOVEL))).isEqualTo("Готичний роман");
        assertThat(Genre.GOTHIC_NOVEL.english()).isEqualTo("Gothic novel");
    }

    @Test
    void toStored_shownUkrainianName_isTheEnglishName() {
        final Messages messages = messagesFor("uk");

        assertThat(Genre.toStored("готичний роман", genre -> messages.get(GenreLabels.keyOf(genre))))
                .isEqualTo("Gothic novel");
    }

    @Test
    void toShown_englishName_isTheUkrainianName() {
        final Messages messages = messagesFor("uk");

        assertThat(Genre.toShown("Gothic novel", genre -> messages.get(GenreLabels.keyOf(genre))))
                .isEqualTo("Готичний роман");
    }
}
