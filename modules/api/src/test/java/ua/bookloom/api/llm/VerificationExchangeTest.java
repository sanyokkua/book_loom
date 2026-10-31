package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

    // IF the shorter forms left the shown bodies empty, THEN the test card would show nothing for an older exchange.
    @Test
    void constructor_elevenArguments_showsTheBodiesAsTheyAreWithNoLoadTimeAndNoCalls() {
        final VerificationExchange exchange = new VerificationExchange(
                "POST", "http://h/x", "{\"a\":1}", "OK", 200, null, null, false, "", null, null);

        assertThat(exchange.shownRequestBody()).isEqualTo("{\"a\":1}");
        assertThat(exchange.shownResponseBody()).isEqualTo("OK");
        assertThat(exchange.loadMs()).isNull();
        assertThat(exchange.calls()).isEmpty();
    }

    // IF the calls list could be changed after construction, THEN a report would change under the card that shows it.
    @Test
    void constructor_mutableCallList_isCopied() {
        final java.util.ArrayList<ProbeCall> calls = new java.util.ArrayList<>();
        calls.add(new ProbeCall(AdvancedCheck.SCHEMA, "{}", "{}", "", 200, java.time.Duration.ofMillis(5), null));
        final VerificationExchange exchange = new VerificationExchange(
                "POST", "http://h/x", "{}", "", 200, null, null, false, "", null, null, "{}", "", 12L, calls);

        calls.clear();

        assertThat(exchange.calls()).hasSize(1);
        assertThat(exchange.loadMs()).isEqualTo(12L);
        assertThat(List.copyOf(exchange.calls()).getFirst().check()).isEqualTo(AdvancedCheck.SCHEMA);
    }
}
