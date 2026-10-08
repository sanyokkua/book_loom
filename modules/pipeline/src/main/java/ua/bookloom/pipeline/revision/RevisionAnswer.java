package ua.bookloom.pipeline.revision;

import java.util.Objects;
import java.util.Optional;
import ua.bookloom.pipeline.heal.GateResult;

/** What one revision call came to: a text the checks let stand, or the reason it was refused. */
sealed interface RevisionAnswer {

    /**
     * The revised text, when the answer is one.
     *
     * @return the text restored through the gate if the checks let it stand, or empty if they refused it
     */
    default Optional<GateResult.Restored> revised() {
        return switch (this) {
            case Revised revised -> Optional.of(revised.restored());
            case Refused refused -> Optional.empty();
        };
    }

    /**
     * A text the checks let stand; it may equal the old one when nothing needed to change.
     *
     * @param restored the text restored through the document gate
     */
    record Revised(GateResult.Restored restored) implements RevisionAnswer {

        /** Rejects a missing text. */
        public Revised {
            Objects.requireNonNull(restored, "restored");
        }
    }

    /**
     * An answer the pass did not keep.
     *
     * @param reason the name of the rule it broke — a guard ({@code quotes}, {@code sentences}…), {@code checks},
     *     {@code worse}, {@code gate}, {@code unreadable}, or an audit check the old text did not fail
     */
    record Refused(String reason) implements RevisionAnswer {

        /** Rejects a missing reason. */
        public Refused {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
