package ua.bookloom.api.llm;

import java.time.Duration;

/**
 * The fixed time and retry limits of a model call. They live here so the window can show exactly what the HTTP client
 * enforces, from one place. A component-less record because {@code :api} holds records, enums and interfaces only, and an
 * interface of constants is a code smell.
 */
public record CallLimits() {

    /** How long a helper call (names, brief, file name, summary) may take. */
    public static final Duration HELPER_TIMEOUT = Duration.ofMinutes(2);

    /** How long a call with no fixed limit may take per token of its expected reply. */
    public static final Duration PER_EXPECTED_TOKEN = Duration.ofMillis(500);

    /** How long a streamed reply may send nothing before the call counts as stalled. */
    public static final Duration STREAM_IDLE = Duration.ofSeconds(60);

    /** The longest any chat call is allowed, whatever its expected length. */
    public static final Duration MAX_CHAT_TIMEOUT = Duration.ofMinutes(10);

    /** The most provider requests for one logical chat. */
    public static final int MAX_ATTEMPTS = 3;

    /** The most of those requests that may end in a timeout. */
    public static final int MAX_TIMEOUT_ATTEMPTS = 2;
}
