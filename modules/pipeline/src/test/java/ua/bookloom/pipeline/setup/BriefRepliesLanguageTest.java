package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The language a window's passage is written in: asked of the model, and read, only when the source is Auto. */
class BriefRepliesLanguageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode reply(final String language) throws Exception {
        return MAPPER.readTree("{\"language\":\"" + language + "\",\"genre\":{\"evidence\":\"\",\"value\":\"\"}}");
    }

    // IF the reply's word were taken as written, THEN "FR" and "uk-UA" would reach the proposal as two odd languages.
    @ParameterizedTest
    @CsvSource(
            nullValues = "none",
            value = {"fr,fr", "FR,fr", "uk-UA,uk", "eng,en", "xx,none", "'',none"})
    void language_autoSource_isTheRepliesLanguageAsANormalTag(final String written, final String expected)
            throws Exception {
        final Optional<String> language = BriefReplies.language(reply(written), null);

        assertThat(language).isEqualTo(Optional.ofNullable(expected));
    }

    // IF a chosen source were second-guessed by the model, THEN the person's choice would be overruled silently.
    @Test
    void language_sourceChosen_isNeverReadWhateverTheReplySays() throws Exception {
        assertThat(BriefReplies.language(reply("fr"), "en")).isEmpty();
    }

    @Test
    void language_replyWithNoLanguage_isEmpty() throws Exception {
        assertThat(BriefReplies.language(MAPPER.readTree("{\"genre\":{\"value\":\"\"}}"), null))
                .isEmpty();
    }
}
