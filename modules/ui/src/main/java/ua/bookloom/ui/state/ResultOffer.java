package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * What a short piece of work offers once its answer needs the person's decision: the action that takes the answer, and
 * the one that drops it. The activity stays registered while the offer is held, so its dialog stays open; the offer ends
 * it, whichever action runs.
 *
 * @param label the catalogue key of the button that takes the answer
 * @param enabled whether the answer can be taken (a failed answer offers nothing, so this is {@code true} when held)
 * @param apply takes the answer and ends the activity
 * @param discard drops the answer and ends the activity
 */
public record ResultOffer(MessageKey label, boolean enabled, Runnable apply, Runnable discard) {

    /** Rejects a missing part. */
    public ResultOffer {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(apply, "apply");
        Objects.requireNonNull(discard, "discard");
    }

    /**
     * The same offer whose two actions also run {@code end} after the caller's own, so taking or dropping the answer
     * ends the activity that holds it.
     *
     * @param end what ends the holding activity
     * @return the offer to store
     */
    ResultOffer ending(final Runnable end) {
        return new ResultOffer(
                label,
                enabled,
                () -> {
                    apply.run();
                    end.run();
                },
                () -> {
                    discard.run();
                    end.run();
                });
    }
}
