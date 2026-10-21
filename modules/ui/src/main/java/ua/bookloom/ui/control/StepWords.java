package ua.bookloom.ui.control;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.StepEntry;

/** How a step of a progress dialog reads: its name, its marker, its one result line and its sentence. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StepWords {

    private static final String ITEM_JOIN = " · ";

    /** The look of each state a step can be in. */
    enum Look {
        /** Planned and not started. */
        PENDING("○", "pending", MessageKey.PROGRESS_STEP_NOT_STARTED),
        /** Started and not ended. */
        RUNNING("●", "running", MessageKey.PROGRESS_STEP_RUNNING),
        /** Ended with its outcome. */
        DONE("✓", "done", MessageKey.PROGRESS_STEP_DONE),
        /** Ended at once, needing no model call. */
        NO_CALL("–", "nocall", MessageKey.PROGRESS_STEP_NO_CALL),
        /** Ended without its outcome. */
        FAILED("✕", "failed", MessageKey.PROGRESS_STEP_FAILED);

        private final String glyph;
        private final String token;
        private final MessageKey result;

        Look(final String glyph, final String token, final MessageKey result) {
            this.glyph = glyph;
            this.token = token;
            this.result = result;
        }

        /** {@return the marker's character} */
        String glyph() {
            return glyph;
        }

        /** {@return the word the marker's style class and the step's sentence select on} */
        String token() {
            return token;
        }

        /** {@return the catalogue entry of the step's result line} */
        MessageKey result() {
            return result;
        }

        static Look of(final StepEntry step) {
            if (step.isPending()) {
                return PENDING;
            }
            if (step.isRunning()) {
                return RUNNING;
            }
            final StepResult ended = step.result();
            return ended == null
                    ? DONE
                    : switch (ended) {
                        case DONE -> DONE;
                        case NO_CALL -> NO_CALL;
                        case FAILED -> FAILED;
                    };
        }
    }

    /** {@return the step's name, with the segment it is about; a planned step is named by its number} */
    static String name(final Messages messages, final StepEntry step) {
        if (step.isPending()) {
            return messages.get(MessageKey.PROGRESS_STEP_PENDING, step.index() + 1);
        }
        final String item = step.itemRef();
        return item == null ? step.label() : step.label() + ITEM_JOIN + item;
    }

    /** {@return the step's one result line: its verdict when it gave one, else its state in words} */
    static String result(final Messages messages, final StepEntry step) {
        final StepEntry.StepText text = step.text();
        final @Nullable String verdict = text == null ? null : text.verdict();
        return verdict != null ? verdict : messages.get(Look.of(step).result());
    }

    /** {@return one plain sentence about the step} */
    static String sentence(final Messages messages, final StepEntry step) {
        return messages.get(MessageKey.PROGRESS_SAY, Look.of(step).token(), name(messages, step));
    }
}
