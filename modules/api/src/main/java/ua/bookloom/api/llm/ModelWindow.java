package ua.bookloom.api.llm;

import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The one context window every call to a model is sized from (design D20): before, during and after a run, whatever
 * the call kind, so a server never reloads the model between call kinds and no call is planned for more room than the
 * model has. An interface of constants because {@code :api} holds records, enums and interfaces only.
 */
public interface ModelWindow {

    /**
     * The window used when nothing smaller is known, and the most any known window is held to: a server may train a
     * model for 128k, but running it that wide costs memory the person did not ask to spend, so only the person's own
     * window goes above it.
     */
    int DEFAULT_WINDOW = 16_384;

    /**
     * The window of one model.
     *
     * @param context what is known about the model's window; non-null. Its loaded figure is set only where the engine
     *     fixes the window at load or server start
     * @param override the window the person set for the model, or null; positive when set, and it wins over everything
     * @return the override, else the smallest of the loaded and detected windows, the profile's hard cap and
     *     {@link #DEFAULT_WINDOW}
     */
    static int of(final ContextPolicy context, final @Nullable Integer override) {
        Objects.requireNonNull(context, "context");
        if (override != null) {
            return override;
        }
        return Stream.of(context.loaded(), context.detected(), context.hardCap(), DEFAULT_WINDOW)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .min()
                .orElse(DEFAULT_WINDOW);
    }
}
