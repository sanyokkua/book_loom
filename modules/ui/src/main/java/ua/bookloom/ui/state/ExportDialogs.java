package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.dialog.ConfirmPrompt;
import ua.bookloom.ui.i18n.Messages;

/**
 * What an export says and asks.
 *
 * @param messages the catalogue its sentences are worded from
 * @param confirm asks before a file is replaced
 * @param announcer says when a book was written
 */
public record ExportDialogs(Messages messages, ConfirmPrompt confirm, ExportAnnouncer announcer) {

    /** Rejects a missing part. */
    @Inject
    public ExportDialogs {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(confirm, "confirm");
        Objects.requireNonNull(announcer, "announcer");
    }
}
