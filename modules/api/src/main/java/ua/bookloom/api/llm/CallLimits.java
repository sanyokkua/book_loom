package ua.bookloom.api.llm;

import java.time.Duration;

/**
 * The fixed time and retry limits of a model call. They live here so the window can show exactly what the HTTP client
 * enforces, from one place. An interface of constants because {@code :api} holds records, enums and interfaces only.
 */
public interface CallLimits {

    /** How long a reviewer call may take. */
    Duration REVIEW_TIMEOUT = Duration.ofSeconds(90);

    /** How long a helper call (names, brief, file name, summary) may take. */
    Duration HELPER_TIMEOUT = Duration.ofMinutes(2);

    /** The longest any chat call is allowed, whatever its expected length. */
    Duration MAX_CHAT_TIMEOUT = Duration.ofMinutes(10);

    /** The most provider requests for one logical chat. */
    int MAX_ATTEMPTS = 3;

    /** The most of those requests that may end in a timeout. */
    int MAX_TIMEOUT_ATTEMPTS = 2;
}
