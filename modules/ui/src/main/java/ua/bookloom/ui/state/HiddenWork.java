package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.notify.Severity;
import ua.bookloom.ui.notify.ToastAction;
import ua.bookloom.ui.notify.Toasts;

/**
 * Long work running in the background: the screen it writes to, which turns read-only meanwhile, and the refusal of
 * other model work with a message that brings its progress back. FX thread only.
 */
@Slf4j
@Singleton
public final class HiddenWork {

    private final ActivityTracker activities;
    private final Toasts toasts;
    private final Messages messages;
    private final ObjectBinding<@Nullable Activity> activity;
    private final ObjectBinding<@Nullable ViewNames> screen;

    /**
     * Follows the running work.
     *
     * @param activities the work under way
     * @param toasts where a refusal is said
     * @param messages the catalogue the hidden work is named from
     */
    @Inject
    public HiddenWork(final ActivityTracker activities, final Toasts toasts, final Messages messages) {
        this.activities = Objects.requireNonNull(activities, "activities");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.activity = Bindings.createObjectBinding(
                () -> activities.running().stream()
                        .filter(Activity::isHidden)
                        .findFirst()
                        .orElse(null),
                activities.running());
        this.screen = Bindings.createObjectBinding(
                () -> {
                    final Activity hidden = activity.get();
                    return hidden == null ? null : hidden.kind().home();
                },
                activity);
    }

    /**
     * The oldest work in the background.
     *
     * @return a binding holding it, or {@code null} while none is hidden
     */
    public ObjectBinding<@Nullable Activity> activity() {
        return activity;
    }

    /**
     * The screen the hidden work writes to, whose editable controls bind their disabled state to it.
     *
     * @return a binding holding the screen, or {@code null} while no work is hidden
     */
    public ObjectBinding<@Nullable ViewNames> screen() {
        return screen;
    }

    /**
     * Refuses work that would compete with the hidden work, saying so with a "Show progress" action. Call it before
     * the owner's own conflict check, so the person reads one message.
     *
     * @param starting the kind about to start
     * @return {@code true} when it was refused and the message raised, {@code false} when no hidden work rules it out
     */
    public boolean refuses(final ActivityKind starting) {
        Objects.requireNonNull(starting, "starting");
        final Activity hidden = activity.get();
        if (hidden == null || !starting.conflictsWith(hidden.kind())) {
            log.debug("hidden work does not rule out {}", starting);
            return false;
        }
        log.warn("{} refused: {} #{} runs in the background", starting, hidden.kind(), hidden.id());
        toasts.raise(
                Severity.WARNING,
                MessageKey.WORK_HIDDEN_REFUSED,
                new ToastAction(
                        MessageKey.WORK_HIDDEN_SHOW,
                        MessageKey.WORK_HIDDEN_SHOW_TIP,
                        () -> activities.reveal(hidden.id())),
                messages.get(hidden.kind().label()));
        return true;
    }
}
