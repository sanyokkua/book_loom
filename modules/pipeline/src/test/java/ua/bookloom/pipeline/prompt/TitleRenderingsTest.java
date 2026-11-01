package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The fixed renderings of forms of address in a language pair: listed titles follow the source's capitalisation. */
class TitleRenderingsTest {

    private static final TitleRenderings EN_UK = TitleRenderings.forPair("en", "uk");

    @ParameterizedTest
    @CsvSource({"mr,пан", "Mr,Пан", "Mrs.,Пані", "ms,пані", "Miss,Панна", "sir,сер", "Dr,Доктор"})
    void render_listedTitle_followsTheSourceCapitalisation(final String term, final String expected) {
        assertThat(EN_UK.render(term)).hasValue(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Hale", "Mr Hale", "constable", ""})
    void render_unlistedTerm_isEmpty(final String term) {
        assertThat(EN_UK.render(term)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"en,de", "de,uk", "en,", ",uk"})
    void render_pairWithNoRows_isEmpty(final String source, final String target) {
        assertThat(TitleRenderings.forPair(source, target).render("mr")).isEmpty();
    }
}
