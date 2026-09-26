package ua.bookloom.ui;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.notify.Toasts;

/** A hand-written {@link Toasts} that records every message raised, with its severity, key and arguments. */
public final class RecordingToasts implements Toasts {

    /** One raised message; the severity is the name of the method that raised it. */
    public record Raised(String severity, MessageKey key, List<Object> args) {}

    private final List<Raised> raised = new CopyOnWriteArrayList<>();

    @Override
    public void success(final MessageKey key, final Object... args) {
        raised.add(new Raised("success", key, List.of(args)));
    }

    @Override
    public void info(final MessageKey key, final Object... args) {
        raised.add(new Raised("info", key, List.of(args)));
    }

    @Override
    public void warning(final MessageKey key, final Object... args) {
        raised.add(new Raised("warning", key, List.of(args)));
    }

    @Override
    public void error(final MessageKey key, final Object... args) {
        raised.add(new Raised("error", key, List.of(args)));
    }

    public List<Raised> raised() {
        return List.copyOf(raised);
    }
}
