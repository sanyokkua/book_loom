package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.Messages;

/** The genre list is exactly forty entries in a fixed order, sent in English and shown in the interface language. */
class GenreTest {

    private static final List<String> EXPECTED = List.of(
            "Literary fiction",
            "Classic literature",
            "Historical fiction",
            "Gothic novel",
            "Romance",
            "Historical romance",
            "Mystery",
            "Detective fiction",
            "Crime fiction",
            "Thriller",
            "Psychological thriller",
            "Horror",
            "Science fiction",
            "Fantasy",
            "Epic fantasy",
            "Dystopian fiction",
            "Adventure",
            "Western",
            "War fiction",
            "Humor",
            "Satire",
            "Young adult",
            "Children's literature",
            "Fairy tale",
            "Short stories",
            "Poetry",
            "Drama",
            "Memoir",
            "Biography",
            "Autobiography",
            "Essay",
            "Travel writing",
            "History",
            "Philosophy",
            "Religion and spirituality",
            "Popular science",
            "Self-help",
            "Business",
            "True crime",
            "Graphic novel");

    @Test
    void values_englishNames_areTheFortyGenresInOrder() {
        assertThat(Arrays.stream(Genre.values()).map(Genre::english).toList()).containsExactlyElementsOf(EXPECTED);
    }

    @Test
    void values_englishNames_areUnique() {
        assertThat(Arrays.stream(Genre.values()).map(Genre::english)).doesNotHaveDuplicates();
    }

    @Test
    void gothicNovel_underUkrainian_isShownInUkrainianAndSentInEnglish() {
        final Messages messages = new Messages((LocaleProvider) () -> Locale.forLanguageTag("uk"));

        assertThat(messages.get(Genre.GOTHIC_NOVEL.messageKey())).isEqualTo("Готичний роман");
        assertThat(Genre.GOTHIC_NOVEL.english()).isEqualTo("Gothic novel");
    }

    @Test
    void toStored_shownUkrainianName_isTheEnglishName() {
        final Messages messages = new Messages((LocaleProvider) () -> Locale.forLanguageTag("uk"));

        assertThat(Genre.toStored("готичний роман", genre -> messages.get(genre.messageKey())))
                .isEqualTo("Gothic novel");
    }

    @Test
    void toStored_textThatNamesNoGenre_isKeptAsWritten() {
        assertThat(Genre.toStored("Cosy mystery set in 1920s Kyiv", Genre::english))
                .isEqualTo("Cosy mystery set in 1920s Kyiv");
    }

    @Test
    void toShown_englishName_isTheDisplayNameAndFreeTextIsKept() {
        final Messages messages = new Messages((LocaleProvider) () -> Locale.forLanguageTag("uk"));

        assertThat(Genre.toShown("Gothic novel", genre -> messages.get(genre.messageKey())))
                .isEqualTo("Готичний роман");
        assertThat(Genre.toShown("Cosy mystery", genre -> messages.get(genre.messageKey())))
                .isEqualTo("Cosy mystery");
    }
}
