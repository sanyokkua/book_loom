package ua.bookloom.pipeline.revision;

import java.util.Objects;
import java.util.Optional;
import ua.bookloom.pipeline.heal.GateResult;

/** What one revision call came to: a text the checks let stand, or the reason it was refused. */
sealed interface RevisionAnswer {

    /** The reason of an answer that was not the required object. */
    String UNREADABLE = "unreadable";

    /** The reason of an answer whose control codes could not be mapped back. */
    String CONTROL_CHARACTERS = "control_chars";

    /**
     * The revised text, when the answer is one.
     *
     * @return the text restored through the gate if the checks let it stand, or empty if they refused it
     */
    default Optional<GateResult.Restored> revised() {
        return switch (this) {
            case Revised revised -> Optional.of(revised.restored());
            case Unchanged unchanged -> Optional.empty();
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

    /** The model's own word that the paragraph needs no change, which it gave without writing the paragraph again. */
    record Unchanged() implements RevisionAnswer {}

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

        /**
         * Whether asking again with the same input could be answered differently, so the refusal says nothing about the
         * paragraph.
         *
         * @return {@code true} for a reply that could not be read, {@code false} for a rule the text broke
         */
        public boolean isUnreadable() {
            return UNREADABLE.equals(reason) || CONTROL_CHARACTERS.equals(reason);
        }
    }
}
