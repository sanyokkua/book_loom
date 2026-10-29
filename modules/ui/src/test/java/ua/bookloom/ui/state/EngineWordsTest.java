package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.ui.i18n.Messages;

/** The engine line and the Advanced features verdicts in both display languages, never in the verifier's words. */
class EngineWordsTest {

    private static Messages messages(final String language) {
        return new Messages(() -> Locale.forLanguageTag(language));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "en | OLLAMA | OLLAMA | 0.40.11 | Detected: Ollama 0.40.11 · native connection",
                "en | OPENAI_COMPATIBLE | LLAMA_CPP | b11669 | Detected: llama-server b11669 · OpenAI-compatible",
                "en | OPENAI_COMPATIBLE | LM_STUDIO | '' | Detected: LM Studio · OpenAI-compatible",
                "en | OPENAI_COMPATIBLE | GENERIC | '' | Not recognised: generic OpenAI-compatible server",
                "uk | OLLAMA | OLLAMA | 0.40.11 | Виявлено: Ollama 0.40.11 · власне з’єднання",
                "uk | OPENAI_COMPATIBLE | UNSLOTH | '' | Виявлено: Unsloth · OpenAI-сумісне",
                "uk | OPENAI_COMPATIBLE | GENERIC | '' | Не розпізнано: звичайний OpenAI-сумісний сервер"
            })
    void line_eachEngine_readsAsTheSpecSays(
            final String language,
            final ProviderKind kind,
            final ServerEngine engine,
            final String version,
            final String expected) {
        final DraftEngine found = new DraftEngine(engine, null, version.isEmpty() ? null : version, false);

        assertThat(EngineWords.line(messages(language), kind, found)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "en | Structured answers: honoured, Turning reasoning off: works with reasoning_effort,"
                        + " top_k/min_p: ignored, Answer cap: max_tokens",
                "uk | Структуровані відповіді: виконується, Вимкнення міркувань: працює з reasoning_effort,"
                        + " top_k/min_p: ігнорується, Обмеження відповіді: max_tokens"
            })
    void verdictLines_genericServerNote_oneLinePerCheck(final String language, final String expected) {
        final String note = "schema=honoured; thinking=reasoning_effort; samplers=ignored; cap=max_tokens";

        assertThat(String.join(", ", EngineWords.verdictLines(messages(language), note)))
                .isEqualTo(expected);
    }
}
