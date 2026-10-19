package ua.bookloom.pipeline.heal;

import java.util.Objects;
import ua.bookloom.api.AppError;
import ua.bookloom.pipeline.prompt.DraftReplyParser;

/**
 * How a self-heal call's reply was classified (design D3 rules 2-3, applied to every self-heal call): a usable
 * rewrite, a reply that is not the required single-target object — which gets no structural-repair retry here,
 * unlike a draft — or content that must flag the segment without another attempt.
 */
public sealed interface RepairReply {

    /**
     * A usable rewrite of the segment's masked target.
     *
     * @param maskedTarget the corrected target, still carrying its {@code ⟦gN⟧} placeholder tokens
     */
    record Rewritten(String maskedTarget) implements RepairReply {

        /** Rejects a missing target. */
        public Rewritten {
            Objects.requireNonNull(maskedTarget, "maskedTarget");
        }
    }

    /**
     * A reply that was not the one required {@code {"target":"…"}} object.
     *
     * @param diagnostic a user-safe explanation of what was wrong with the reply
     */
    record Malformed(String diagnostic) implements RepairReply {

        /** Rejects a missing diagnostic. */
        public Malformed {
            Objects.requireNonNull(diagnostic, "diagnostic");
        }

        /**
         * The machine code of the cause, which a report turns into words.
         *
         * @return {@code control_chars} for a reply whose control codes could not be mapped back, else
         *     {@code unreadable}
         */
        public String reason() {
            return DraftReplyParser.CONTROL_CHARACTERS_DIAGNOSTIC.equals(diagnostic) ? "control_chars" : "unreadable";
        }
    }

    /**
     * Content that must flag the segment now: an empty or whitespace-only reply, a reply cut off before it
     * finished, or a model call that itself answered {@code emptyCompletion}/{@code contextWindow}.
     *
     * @param error the error to record against the segment
     */
    record FlagNow(AppError error) implements RepairReply {

        /** Rejects a missing error. */
        public FlagNow {
            Objects.requireNonNull(error, "error");
        }
    }
}
