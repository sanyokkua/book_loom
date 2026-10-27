package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VerificationExchangeTest {

    @Test
    void constructor_eightArguments_hasNoHeadersAndNoTokenCounts() {
        final VerificationExchange exchange =
                new VerificationExchange("GET", "http://h/x", "", "{}", 200, null, null, false);

        assertThat(exchange.requestHeaders()).isEmpty();
        assertThat(exchange.promptTokens()).isNull();
        assertThat(exchange.completionTokens()).isNull();
    }

    @Test
    void constructor_nineArguments_hasNoTokenCounts() {
        final VerificationExchange exchange =
                new VerificationExchange("GET", "http://h/x", "", "{}", 200, null, null, false, "A: b");

        assertThat(exchange.requestHeaders()).isEqualTo("A: b");
        assertThat(exchange.promptTokens()).isNull();
        assertThat(exchange.completionTokens()).isNull();
    }

    @Test
    void constructor_elevenArguments_carriesTheTokenCounts() {
        final VerificationExchange exchange =
                new VerificationExchange("POST", "http://h/x", "{}", "{}", 200, 41L, 22.5, true, "", 18, 7);

        assertThat(exchange.promptTokens()).isEqualTo(18);
        assertThat(exchange.completionTokens()).isEqualTo(7);
    }
}
