package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Language names come from the catalogue under English and from ICU otherwise, and typed text parses to a tag. */
class LanguageNamesTest {

    private static final Locale EN = Locale.ENGLISH;
    private static final Locale UK = Locale.forLanguageTag("uk");

    private final LanguageNames names = new LanguageNames();

    @Test
    void list_english_holdsThirtyFourTagsStartingWithBelarusian() {
        final List<String> tags = names.list(EN);

        assertThat(tags).hasSize(34).startsWith("be");
        assertThat(tags.stream().map(tag -> names.nameOf(tag, EN)))
                .contains("Norwegian Bokmål", "Chinese (Traditional)", "Irish");
    }

    @Test
    void list_ukrainian_showsNoRawTag() {
        assertThat(names.list(UK)).noneMatch(tag -> names.nameOf(tag, UK).equals(tag));
    }

    @Test
    void nameOf_tags_readInTheInterfaceLanguage() {
        assertThat(names.nameOf("uk", UK)).isEqualTo("українська");
        assertThat(names.nameOf("la", UK)).isEqualTo("латинська");
        assertThat(names.nameOf("la", EN)).isEqualTo("Latin");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Latin|en|la",
                "la|en|la",
                "  LATIN |en|la",
                "латинська|uk|la",
                "Ukrainian|uk|uk",
                "Norwegian Bokmal|en|nb",
                "en-US|en|en",
                "haw|en|haw",
                "Elvish|en|",
                "|en|",
                "xx-yy|en|"
            })
    void parse_typedText_givesTheTagOrNothing(final String text, final String locale, final String expected) {
        final Optional<String> tag = names.parse(text, Locale.forLanguageTag(locale));

        assertThat(tag).isEqualTo(Optional.ofNullable(expected));
    }

    // IF a list showed "українська" under "Авто — ...", THEN one list would mix a capital with lower-case entries.
    @ParameterizedTest
    @CsvSource({"uk, uk, Українська", "la, uk, Латинська", "la, en, Latin", "nb, en, Norwegian Bokmål"})
    void listName_tag_startsWithACapitalAsAListEntry(final String tag, final String locale, final String expected) {
        assertThat(names.listName(tag, Locale.forLanguageTag(locale))).isEqualTo(expected);
    }

    // IF only the Ukrainian name were searched, THEN typing "Ukr" in the Ukrainian interface would find nothing.
    @Test
    void searchText_ukrainianInterface_holdsTheEnglishNameAndTheTag() {
        assertThat(names.searchText("uk", UK)).contains("Українська", "Ukrainian", "uk");
    }
}
