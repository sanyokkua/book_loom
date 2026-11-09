package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/** A refusal rule is worded in words in the call panel's detail, and an unknown one shows as its own code. */
class RefusalVocabularyTest {

    private final Injector injector = UiTestInjector.create(Locale.ENGLISH);
    private final Messages messages = injector.getInstance(Messages.class);

    @ParameterizedTest
    @CsvSource({
        "quotes,quote marks not paired",
        "sentences,sentences lost",
        "words,words lost",
        "dashes,dashes lost",
        "latin_run,English words left",
        "gate,refused by the checks",
        "control_chars,quote marks sent as control codes",
        "worse,scored worse"
    })
    void rule_inTheCallDetail_isWordedInWords(final String rule, final String words) {
        assertThat(messages.code(MessageKey.LIVE_CALL_DETAIL, rule)).isEqualTo(words);
    }

    @Test
    void rule_unknownToTheCatalogue_isShownAsItsOwnCode() {
        assertThat(messages.code(MessageKey.LIVE_CALL_DETAIL, "brand_new_rule")).isEqualTo("brand_new_rule");
    }
}
