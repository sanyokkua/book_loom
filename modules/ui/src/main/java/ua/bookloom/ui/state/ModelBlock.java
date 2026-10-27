package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.Optional;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.Messages;

/**
 * Why the model actions of Names &amp; style are not offered: other model work runs. One line serves all of them, since
 * the scan, the review, the translation and the automatic pass are ruled out by the same work; a translation at work is
 * told in the README's words ({@link BlockedWords}). FX thread only.
 */
@Slf4j
final class ModelBlock {

    private final ActivityTracker activities;
    private final Messages messages;
    private final ReadOnlyBooleanProperty busy;
    private final ObjectBinding<@Nullable ActivityKind> blocker;
    private final ReadOnlyStringWrapper reason = new ReadOnlyStringWrapper("");

    ModelBlock(final ActivityTracker activities, final Messages messages, final ReadOnlyBooleanProperty busy) {
        this.activities = Objects.requireNonNull(activities, "activities");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.busy = Objects.requireNonNull(busy, "busy");
        this.blocker = activities.blocker(ActivityKind.GLOSSARY_SCAN);
        blocker.addListener(observed -> refresh());
        busy.addListener(observed -> refresh());
        refresh();
    }

    /** {@return a read-only line, empty while the actions are offered or one of them runs itself} */
    ReadOnlyStringProperty reason() {
        return reason.getReadOnlyProperty();
    }

    /**
     * Says whether other work rules {@code what} out right now.
     *
     * @param what the kind about to start
     * @return the line to show, or empty when it may start
     */
    Optional<String> refusalFor(final ActivityKind what) {
        final Optional<ActivityKind> conflict = activities.conflictFor(what);
        conflict.ifPresent(kind -> log.debug("the model {} was not started: {} is running", what, kind));
        return conflict.map(kind -> BlockedWords.in(messages, kind, what));
    }

    private void refresh() {
        final ActivityKind kind = blocker.get();
        final String line =
                kind == null || busy.get() ? "" : BlockedWords.in(messages, kind, ActivityKind.GLOSSARY_SCAN);
        if (!line.equals(reason.get())) {
            log.debug("glossary model actions blocked by {}", kind);
        }
        reason.set(line);
    }
}
