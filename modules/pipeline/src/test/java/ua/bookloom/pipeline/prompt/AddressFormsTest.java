package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The language file lists the forms of the informal and the formal "you" that the address-mix check reads. */
class AddressFormsTest {

    @Test
    void informal_ukrainian_listsTheTyForms() {
        assertThat(AddressForms.informal("uk")).contains("ти", "тебе", "тобі", "тобою", "твій", "твоя", "твоє", "твої");
    }

    @Test
    void formal_ukrainian_listsTheCapitalisedVyForms() {
        assertThat(AddressForms.formal("uk")).contains("Ви", "Вас", "Вам", "Вами", "Ваш", "Ваша", "Ваше", "Ваші");
    }

    // IF a formal form were listed in lower case, THEN every plural "ви" of a speech would count as polite.
    @Test
    void formal_ukrainian_everyFormStartsWithACapital() {
        assertThat(AddressForms.formal("uk")).isNotEmpty().allMatch(form -> Character.isUpperCase(form.charAt(0)));
    }

    @Test
    void informal_ukrainian_everyFormIsLowerCase() {
        assertThat(AddressForms.informal("uk"))
                .isNotEmpty()
                .allMatch(form -> form.equals(form.toLowerCase(Locale.ROOT)));
    }

    @Test
    void forms_ukrainian_shareNoWordOnceCaseIsIgnored() {
        assertThat(AddressForms.formal("uk").stream().map(form -> form.toLowerCase(Locale.ROOT)))
                .doesNotContainAnyElementsOf(AddressForms.informal("uk"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "pl", "ru", "de", "xx"})
    void forms_languageWithoutTheKeys_areEmpty(final String tag) {
        assertThat(AddressForms.informal(tag)).isEmpty();
        assertThat(AddressForms.formal(tag)).isEmpty();
    }
}
