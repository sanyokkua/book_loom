package ua.bookloom.llm.http;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Keeps a reply body for the Detailed view of a call, cut when it is long so a long run holds a bounded amount. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RawBodies {

    // A streamed reply is one JSON line per token; this is plenty to read and bounds what the run keeps.
    static final int KEPT_CHARS = 32_000;

    /**
     * The body as kept.
     *
     * @param body the non-null reply body
     * @return the body, or its first {@value #KEPT_CHARS} characters followed by a note of what was left out
     */
    public static String kept(final String body) {
        if (body.length() <= KEPT_CHARS) {
            return body;
        }
        return body.substring(0, KEPT_CHARS) + "\n… " + (body.length() - KEPT_CHARS) + " more characters";
    }
}
