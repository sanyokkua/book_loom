package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.i18n.Messages;

/** The wording of each model note in English and Ukrainian: present, distinct, and saying what the owner asked for. */
class ModelAdviceMessagesTest {

    private static final Locale UKRAINIAN = Locale.forLanguageTag("uk");

    private final Messages english = UiTestInjector.create(Locale.ENGLISH).getInstance(Messages.class);
    private final Messages ukrainian = UiTestInjector.create(UKRAINIAN).getInstance(Messages.class);

    private String text(final Locale locale, final ProviderKind kind, final String model, final int index) {
        final Messages messages = locale.equals(Locale.ENGLISH) ? english : ukrainian;
        return messages.get(ModelAdvice.notesFor(kind, model).get(index).text());
    }

    @Test
    void english_eachNote_saysWhatItIsFor() {
        assertThat(text(Locale.ENGLISH, ProviderKind.OLLAMA, "gemma4:26b-mlx", 0))
                .contains("Recommended")
                .contains("26B");
        assertThat(text(Locale.ENGLISH, ProviderKind.OLLAMA, "gemma4:e4b-mlx", 0))
                .contains("E4B")
                .contains("minimum");
        assertThat(text(Locale.ENGLISH, ProviderKind.OLLAMA, "gemma4:e2b-mlx", 0))
                .contains("E2B")
                .contains("Not recommended");
        assertThat(text(Locale.ENGLISH, ProviderKind.OLLAMA, "gpt-oss:20b", 0))
                .contains("gpt-oss")
                .contains("Not recommended");
        assertThat(text(Locale.ENGLISH, ProviderKind.OPENAI_COMPATIBLE, "x", 0))
                .contains("LM Studio")
                .contains("slower")
                .contains("does not stream")
                .contains("control codes");
    }

    @Test
    void ukrainian_eachNote_isWrittenInUkrainian() {
        assertThat(text(UKRAINIAN, ProviderKind.OLLAMA, "gemma4:26b-mlx", 0))
                .contains("Рекомендовано")
                .contains("26B");
        assertThat(text(UKRAINIAN, ProviderKind.OLLAMA, "gemma4:e4b-mlx", 0))
                .contains("E4B")
                .contains("мінімум");
        assertThat(text(UKRAINIAN, ProviderKind.OLLAMA, "gemma4:e2b-mlx", 0))
                .contains("E2B")
                .contains("Не рекомендовано");
        assertThat(text(UKRAINIAN, ProviderKind.OLLAMA, "gpt-oss:20b", 0))
                .contains("gpt-oss")
                .contains("Не рекомендовано");
        assertThat(text(UKRAINIAN, ProviderKind.OPENAI_COMPATIBLE, "x", 0))
                .contains("LM Studio")
                .contains("повільніший")
                .contains("не передає")
                .contains("керівних кодів");
    }

    // IF a key were missing from one bundle, THEN that language would show the raw key; the two texts must differ.
    @Test
    void everyNote_englishAndUkrainian_areDifferentNonBlankTexts() {
        final List<String> models = List.of("gemma4:26b-mlx", "gemma4:e4b-mlx", "gemma4:e2b-mlx", "gpt-oss:20b");
        models.forEach(model -> {
            final String inEnglish = text(Locale.ENGLISH, ProviderKind.OLLAMA, model, 0);
            final String inUkrainian = text(UKRAINIAN, ProviderKind.OLLAMA, model, 0);
            assertThat(inEnglish).isNotBlank().isNotEqualTo(inUkrainian);
            assertThat(inUkrainian).isNotBlank();
        });
    }
}
