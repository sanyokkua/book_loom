package ua.bookloom.api.llm;

import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A setting a model refused, kept so it is not sent again until the person says so.
 *
 * @param name the refused setting, such as {@code temperature}; non-null
 * @param at the day the model refused it; non-null
 * @param insteadDid what BookLoom did in place of the setting; non-null
 * @param scope the one call kind the refusal covers, such as a prompt whose answer schema was refused, or null when it
 *     covers every call to the model; a schema one call kind cannot use says nothing about the others
 */
public record RefusedSetting(
        String name,
        LocalDate at,
        String insteadDid,
        @Nullable String scope) {

    public RefusedSetting {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(insteadDid, "insteadDid");
    }

    /**
     * A refusal that covers every call to the model.
     *
     * @param name the refused setting; non-null
     * @param at the day the model refused it; non-null
     * @param insteadDid what BookLoom did in place of the setting; non-null
     */
    public RefusedSetting(final String name, final LocalDate at, final String insteadDid) {
        this(name, at, insteadDid, null);
    }
}
