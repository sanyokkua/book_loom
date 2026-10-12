package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The forty genres, moved from the interface to {@code :api} (15i.B4) so the brief's synthesis can hold the model to
 * them: exactly forty in a fixed order, sent by their English name, found again from any spelling of that name.
 */
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

    // IF the model's "gothic NOVEL " were not the listed genre, THEN a right answer in the wrong case would be refused.
    @ParameterizedTest
    @CsvSource({"Gothic novel, GOTHIC_NOVEL", "gothic NOVEL, GOTHIC_NOVEL", "'  self-help ', SELF_HELP"})
    void ofEnglish_anySpellingOfAListedName_isThatGenre(final String text, final Genre expected) {
        assertThat(Genre.ofEnglish(text)).contains(expected);
    }

    // IF a genre outside the forty were taken, THEN the brief would hold a label no screen can show in Ukrainian.
    @ParameterizedTest
    @ValueSource(strings = {"Cyberpunk saga", "gothic", "", "novel"})
    void ofEnglish_textThatIsNoListedName_isEmpty(final String text) {
        assertThat(Genre.ofEnglish(text)).isEmpty();
    }

    @Test
    void toStored_shownDisplayNameInAnyCase_isTheEnglishName() {
        assertThat(Genre.toStored("GOTHIC NOVEL", Genre::english)).isEqualTo("Gothic novel");
    }

    @Test
    void toStored_textThatNamesNoGenre_isKeptAsWritten() {
        assertThat(Genre.toStored("Cosy mystery set in a harbour town", Genre::english))
                .isEqualTo("Cosy mystery set in a harbour town");
    }

    @Test
    void toShown_englishName_isTheDisplayNameAndFreeTextIsKept() {
        assertThat(Genre.toShown("Gothic novel", genre -> "[" + genre.name() + "]"))
                .isEqualTo("[GOTHIC_NOVEL]");
        assertThat(Genre.toShown("Cosy mystery", genre -> "[" + genre.name() + "]"))
                .isEqualTo("Cosy mystery");
    }
}
