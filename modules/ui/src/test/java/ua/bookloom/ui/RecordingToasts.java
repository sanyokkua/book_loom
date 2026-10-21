package ua.bookloom.ui;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.notify.Severity;
import ua.bookloom.ui.notify.ToastAction;
import ua.bookloom.ui.notify.ToastOccasion;
import ua.bookloom.ui.notify.Toasts;

/** A hand-written {@link Toasts} that records every message raised and every footer line said, with severity, key and arguments. */
public final class RecordingToasts implements Toasts {

    /** One raised message; the severity is the name of the method that raised it. */
    public record Raised(String severity, MessageKey key, List<Object> args) {}

    private final List<Raised> raised = new CopyOnWriteArrayList<>();
    private final List<ToastAction> actions = new CopyOnWriteArrayList<>();
    private final List<Raised> feedback = new CopyOnWriteArrayList<>();

    @Override
    public void raise(final ToastOccasion occasion, final Object... args) {
        raised.add(new Raised(severityName(occasion), occasion.key(), List.of(args)));
    }

    @Override
    public void raiseWithAction(final ToastOccasion occasion, final ToastAction action, final Object... args) {
        raised.add(new Raised(severityName(occasion), occasion.key(), List.of(args)));
        actions.add(action);
    }

    @Override
    public void feedback(final Severity severity, final MessageKey key, final Object... args) {
        feedback.add(new Raised(severity.name().toLowerCase(Locale.ROOT), key, List.of(args)));
    }

    private static String severityName(final ToastOccasion occasion) {
        return occasion.severity().name().toLowerCase(Locale.ROOT);
    }

    /** The actions of the messages raised with one, in order. */
    public List<ToastAction> actions() {
        return List.copyOf(actions);
    }

    /** The footer lines said for an open dialog, in order. */
    public List<Raised> feedback() {
        return List.copyOf(feedback);
    }

    public List<Raised> raised() {
        return List.copyOf(raised);
    }
}
