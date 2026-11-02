package ua.bookloom.ui.notify;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.i18n.Messages;

/**
 * How a service speaks to the person: the catalogue its sentences come from and the place a failure is shown.
 *
 * @param messages the catalogue the sentences are worded from
 * @param errors where a failure is shown
 */
public record Voice(Messages messages, ErrorPresenter errors) {

    /** Rejects a missing part. */
    @Inject
    public Voice {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(errors, "errors");
    }
}
