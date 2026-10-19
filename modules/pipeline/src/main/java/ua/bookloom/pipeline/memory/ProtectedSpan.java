package ua.bookloom.pipeline.memory;

import java.util.Objects;
import ua.bookloom.pipeline.qa.CheckName;

/**
 * One piece of a segment's text hidden behind a token while the model drafts it.
 *
 * @param token the {@code ⟦gN⟧} token shown to the model in place of the text
 * @param restored what the token becomes again: a locked entry's target, or the exact masked substring of a kept run
 *     from its opening to its closing document token
 * @param check the hard gate a missing or repeated token fails: {@link CheckName#LOCKED_TERM} or
 *     {@link CheckName#KEPT_RUN}
 */
public record ProtectedSpan(String token, String restored, CheckName check) {

    /** Rejects a missing component. */
    public ProtectedSpan {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(restored, "restored");
        Objects.requireNonNull(check, "check");
    }
}
