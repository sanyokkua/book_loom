package ua.bookloom.api.llm;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A setting a model refused, kept so it is not sent again until the person says so.
 *
 * @param name the refused setting, such as {@code temperature}; non-null
 * @param at the day the model refused it; non-null
 * @param insteadDid what BookLoom did in place of the setting; non-null
 */
public record RefusedSetting(String name, LocalDate at, String insteadDid) {

    public RefusedSetting {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(insteadDid, "insteadDid");
    }
}
