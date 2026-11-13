package ua.bookloom.api;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The bounds {@link SafeDetails} puts on the text it holds. Not a filter for secrets — the record's component set is
 * what keeps secrets out; these bound the blast radius of a value passed to the wrong field: a newline would otherwise
 * break a log line into two, and an unbounded value would turn an error dialog into a data dump.
 */
enum SafeText {
// A constant-less enum, so nothing can make an instance: :api has no Lombok for ADR-0024's private constructor,
// and the records-first rule holds every top-level class in an api package to be a record.
;

    /** Longest text a single allowlisted field may contribute, in characters. */
    private static final int MAX_FIELD_LENGTH = 120;

    /**
     * Most observed placeholder tokens rendered before the remainder is summarised as a count. Comfortably above
     * the forty-placeholder segment that motivated lifting the character cap on this field, and low enough that a
     * degenerate provider response cannot turn the rendered details into a data dump.
     */
    private static final int MAX_OBSERVED_TOKENS = 64;

    /** Longest a single observed placeholder token may render at; a real one is at most a handful of characters. */
    private static final int MAX_OBSERVED_TOKEN_LENGTH = 16;

    /** Matches any run of control or whitespace characters, which collapse to a single space. */
    private static final String CONTROL_OR_SPACE = "[\\p{Cntrl}\\s]+";

    /** Flattens a value to one bounded, single-line token. */
    static String flatten(String value) {
        final String flattened = value.replaceAll(CONTROL_OR_SPACE, " ").trim();
        return flattened.length() <= MAX_FIELD_LENGTH ? flattened : flattened.substring(0, MAX_FIELD_LENGTH) + "…";
    }

    /** Renders the model-derived placeholder side under both bounds, stating any overflow instead of dropping it. */
    static String observed(List<String> observed) {
        final String rendered = observed.stream()
                .limit(MAX_OBSERVED_TOKENS)
                .map(SafeText::boundToken)
                .collect(Collectors.joining(" "));
        final int overflow = observed.size() - MAX_OBSERVED_TOKENS;
        return overflow > 0 ? rendered + " +" + overflow + " more" : rendered;
    }

    /**
     * Caps one observed token's length. A token of the real grammar is at most a handful of characters, so a
     * longer one is not a token the repair tier can use anyway — the ellipsis both bounds it and marks it as
     * something the model invented rather than something the gate can act on.
     */
    private static String boundToken(String token) {
        return token.length() <= MAX_OBSERVED_TOKEN_LENGTH
                ? token
                : token.substring(0, MAX_OBSERVED_TOKEN_LENGTH) + "…";
    }
}
