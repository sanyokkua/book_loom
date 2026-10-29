package ua.bookloom.llm.http;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * The prompt size and the window a context-overflow reply states, so the flag can say "696 of 512 tokens". Read only
 * from two known shapes; any other wording yields nothing rather than a guess.
 *
 * @param prompt the tokens the request needed
 * @param window the tokens the model's window holds
 */
@Slf4j
record TokenCounts(int prompt, int window) {

    // "request (696 tokens) exceeds the available context size (512 tokens)": the verb between the two counts.
    private static final Pattern VERB_FIRST = Pattern.compile(
            "\\((\\d{1,9})\\s+tokens?\\)[^()]{0,200}?\\bexceed\\w*\\b[^()]{0,200}?\\bcontext\\b[^()]{0,200}?"
                    + "\\((\\d{1,9})\\s+tokens?\\)",
            Pattern.CASE_INSENSITIVE);
    // llama-server's typed error object.
    private static final Pattern TYPED_PROMPT = Pattern.compile("\"n_prompt_tokens\"\\s*:\\s*(\\d{1,9})");
    private static final Pattern TYPED_WINDOW = Pattern.compile("\"n_ctx\"\\s*:\\s*(\\d{1,9})");

    /**
     * Reads the two counts from a reply's body.
     *
     * @param body the reply's body; searched, never logged
     * @return both counts if the body states them in a known shape, or empty if it does not
     */
    static Optional<TokenCounts> in(final String body) {
        Objects.requireNonNull(body, "body");
        final Optional<TokenCounts> found = verbFirst(body).or(() -> typed(body));
        log.debug(
                "token counts found={} prompt={} window={}",
                found.isPresent(),
                found.map(TokenCounts::prompt).orElse(null),
                found.map(TokenCounts::window).orElse(null));
        return found;
    }

    private static Optional<TokenCounts> verbFirst(final String body) {
        final Matcher matcher = VERB_FIRST.matcher(body);
        return matcher.find()
                ? Optional.of(new TokenCounts(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))))
                : Optional.empty();
    }

    private static Optional<TokenCounts> typed(final String body) {
        final Matcher prompt = TYPED_PROMPT.matcher(body);
        final Matcher window = TYPED_WINDOW.matcher(body);
        return prompt.find() && window.find()
                ? Optional.of(new TokenCounts(Integer.parseInt(prompt.group(1)), Integer.parseInt(window.group(1))))
                : Optional.empty();
    }
}
