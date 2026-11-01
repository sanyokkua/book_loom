package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.Address;
import ua.bookloom.api.project.AddressForm;
import ua.bookloom.api.project.QuoteStyle;

/** What a target language offers the Book Brief: the first choice listed is the file's default. */
class BriefChoicesTest {

    private static final QuoteStyle ANGLE = new QuoteStyle("«", "»", "„", "“");
    private static final QuoteStyle LOW = new QuoteStyle("„", "“", "‚", "‘");

    @Test
    void defaults_firstListedChoices_areTheDefaults() {
        final BriefChoices choices = new BriefChoices(
                List.of("’", "'"),
                List.of(ANGLE, LOW),
                new BriefChoices.AddressWords("ти", "Ви"),
                List.of(new BriefChoices.DeclaredConvention("hardG", List.of("г", "ґ"), "г")));

        assertThat(choices.defaultApostrophe()).isEqualTo("’");
        assertThat(choices.defaultQuoteStyle()).isEqualTo(ANGLE);
        assertThat(choices.defaultAddress()).isEqualTo(new Address(AddressForm.FORMAL, AddressForm.INFORMAL));
        assertThat(choices.isEmpty()).isFalse();
    }

    @Test
    void none_offersNothing() {
        assertThat(BriefChoices.NONE.isEmpty()).isTrue();
        assertThat(BriefChoices.NONE.defaultApostrophe()).isNull();
        assertThat(BriefChoices.NONE.defaultQuoteStyle()).isNull();
        assertThat(BriefChoices.NONE.defaultAddress()).isNull();
    }

    @Test
    void convention_defaultNotAmongItsChoices_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BriefChoices.DeclaredConvention("hardG", List.of("г", "ґ"), "x"));
    }

    @Test
    void convention_oneChoice_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BriefChoices.DeclaredConvention("hardG", List.of("г"), "г"));
    }

    @Test
    void languageSupport_lambda_offersNoChoices() {
        final LanguageSupport support = tag -> true;

        assertThat(support.briefChoices("uk")).isEqualTo(BriefChoices.NONE);
    }
}
