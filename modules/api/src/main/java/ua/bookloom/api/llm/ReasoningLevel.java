package ua.bookloom.api.llm;

import java.util.Objects;
import ua.bookloom.api.settings.ReasoningSetting;

/** How much a call lets a thinking model think; a null level in a request means the provider's own default. */
public enum ReasoningLevel {
    OFF,
    LOW,
    ON;

    /**
     * Whether the model reasons at all, which decides the output-cap headroom a reply needs.
     *
     * @return {@code true} for {@link #LOW} and {@link #ON}, {@code false} for {@link #OFF}
     */
    public boolean isThinking() {
        return this != OFF;
    }

    /**
     * Maps the stored Generation setting to the level a request carries.
     *
     * @param setting the stored setting; never null
     * @return the level of the same name
     */
    public static ReasoningLevel of(final ReasoningSetting setting) {
        Objects.requireNonNull(setting, "setting");
        return switch (setting) {
            case OFF -> OFF;
            case LOW -> LOW;
            case ON -> ON;
        };
    }
}
