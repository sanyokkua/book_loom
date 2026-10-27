package ua.bookloom.ui.state;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.VerificationExchange;

/** Writes a verification request as a curl command a person can run by hand. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CurlCommand {

    /**
     * The command for a request.
     *
     * @param exchange the request as the verifier kept it, headers already masked
     * @return the command on one line; it adds and resolves no header, so a key stays {@code ••••}
     */
    public static String of(final VerificationExchange exchange) {
        final StringBuilder command = new StringBuilder("curl -X ")
                .append(exchange.method())
                .append(' ')
                .append(quoted(exchange.url()));
        exchange.requestHeaders()
                .lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .forEach(line -> command.append(" -H ").append(quoted(line)));
        if (!exchange.requestBody().isEmpty()) {
            command.append(" --data-raw ").append(quoted(exchange.requestBody()));
        }
        return command.toString();
    }

    private static String quoted(final String text) {
        return "'" + text.replace("'", "'\\''") + "'";
    }
}
