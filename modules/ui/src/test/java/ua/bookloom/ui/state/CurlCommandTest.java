package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.VerificationExchange;

/** The request as curl: what was sent, with the key still hidden. */
class CurlCommandTest {

    private static VerificationExchange exchange(
            final String method, final String url, final String body, final String headers) {
        return new VerificationExchange(method, url, body, "", 200, null, null, false, headers);
    }

    // IF the command resolved or invented a header, THEN the key could end up in a clipboard.
    @Test
    void of_postWithMaskedBearer_keepsTheMaskAndWritesTheBody() {
        final VerificationExchange post = exchange(
                "POST",
                "http://127.0.0.1:1234/v1/chat/completions",
                "{\"model\":\"qwen\"}",
                "Authorization: Bearer ••••\nContent-Type: application/json");

        assertThat(CurlCommand.of(post))
                .isEqualTo("curl -X POST 'http://127.0.0.1:1234/v1/chat/completions'"
                        + " -H 'Authorization: Bearer ••••' -H 'Content-Type: application/json'"
                        + " --data-raw '{\"model\":\"qwen\"}'");
    }

    // IF a request without a body got an empty data flag, THEN a plain listing would be sent as a POST body.
    @Test
    void of_getWithNoBodyAndNoHeaders_isTheBareCommand() {
        assertThat(CurlCommand.of(exchange("GET", "http://127.0.0.1:11434/api/tags", "", "")))
                .isEqualTo("curl -X GET 'http://127.0.0.1:11434/api/tags'");
    }

    // IF a quote in the text ended the shell string, THEN the rest would run as a command.
    @Test
    void of_singleQuoteInTheBody_isEscapedForTheShell() {
        assertThat(CurlCommand.of(exchange("POST", "http://127.0.0.1:11434/api/chat", "it's", "")))
                .isEqualTo("curl -X POST 'http://127.0.0.1:11434/api/chat' --data-raw 'it'\\''s'");
    }

    @Test
    void of_blankHeaderLines_areSkipped() {
        assertThat(CurlCommand.of(exchange("GET", "http://127.0.0.1:11434/api/tags", "", "\n  \nAccept: */*\n")))
                .isEqualTo("curl -X GET 'http://127.0.0.1:11434/api/tags' -H 'Accept: */*'");
    }
}
