package ua.bookloom.llm.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The two counts are read only from the shapes known to state them; any other wording yields none, never a guess. */
class TokenCountsTest {

    @Test
    void in_verbFirstSentence_readsPromptThenWindow() {
        assertThat(TokenCounts.in("request (696 tokens) exceeds the available context size (512 tokens)"))
                .contains(new TokenCounts(696, 512));
    }

    @Test
    void in_typedLlamaServerObject_readsBothFields() {
        assertThat(TokenCounts.in("{\"n_prompt_tokens\":9120,\"n_ctx\":8192}")).contains(new TokenCounts(9120, 8192));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "input length 9120 is greater than the context length 8192",
                "{\"n_ctx\":8192}",
                "context (512 tokens) is smaller than the request (696 tokens)"
            })
    void in_otherWording_isEmpty(final String body) {
        assertThat(TokenCounts.in(body)).isEmpty();
    }
}
