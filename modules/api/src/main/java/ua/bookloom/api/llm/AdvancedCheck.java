package ua.bookloom.api.llm;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * One check of the optional Advanced features stage, as its {@link StageOutcome#note()} names it.
 *
 * <p>The verifier knows no display language, so the stage's note is machine words: {@code key=value} pairs joined by
 * {@value #SEPARATOR}, in this enum's order, one per check that ran (for example
 * {@code schema=honoured; thinking=reasoning_effort; samplers=ignored; cap=max_tokens}). The window words each pair
 * in its own language through {@link #read(String)}.
 */
public enum AdvancedCheck {
    /** The answer schema; the value is an {@link ProbeVerdicts.Outcome} in lower case. */
    SCHEMA("schema"),
    /** Turning reasoning off; the value is the request field that worked, or the outcome in lower case when none did. */
    THINKING("thinking"),
    /** The extra samplers {@code top_k} and {@code min_p}; the value is the outcome in lower case. */
    SAMPLERS("samplers"),
    /** The answer-cap field; the value is the wire name of the field that limits the answer, or {@value #NO_FIELD}. */
    CAP("cap");

    /** What joins two pairs of the note. */
    public static final String SEPARATOR = "; ";

    /** The cap check's value when no field limits the answer. */
    public static final String NO_FIELD = "none";

    private final String token;

    AdvancedCheck(final String token) {
        this.token = Objects.requireNonNull(token, "token");
    }

    /**
     * The key this check is written under in the note.
     *
     * @return a lower-case word; never null
     */
    public String token() {
        return token;
    }

    /**
     * One pair of the note.
     *
     * @param value the check's value; non-null
     * @return {@code token=value}
     */
    public String pair(final String value) {
        Objects.requireNonNull(value, "value");
        return token + "=" + value;
    }

    /**
     * Reads the pairs back from a note.
     *
     * @param note the stage's note, or {@code null}
     * @return the value of each check the note names, in this enum's order; never null, empty when none is named
     */
    public static Map<AdvancedCheck, String> read(final @Nullable String note) {
        if (note == null) {
            return Map.of();
        }
        final Map<AdvancedCheck, String> found = new EnumMap<>(AdvancedCheck.class);
        for (final String part : note.split(SEPARATOR.strip(), -1)) {
            final int equals = part.indexOf('=');
            if (equals > 0) {
                final String key = part.substring(0, equals).strip();
                byToken(key)
                        .ifPresent(check ->
                                found.put(check, part.substring(equals + 1).strip()));
            }
        }
        return Collections.unmodifiableMap(found);
    }

    private static Optional<AdvancedCheck> byToken(final String key) {
        return Arrays.stream(values()).filter(check -> check.token.equals(key)).findFirst();
    }
}
