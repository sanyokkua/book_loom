package ua.bookloom.app;

import java.util.Objects;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.EnvironmentVariables;

/** Reads the process environment; the value is looked at only to see that it is not blank and is never kept or logged. */
@Slf4j
final class SystemEnvironmentVariables implements EnvironmentVariables {

    private final Function<String, @Nullable String> lookup;

    SystemEnvironmentVariables() {
        this(System::getenv);
    }

    SystemEnvironmentVariables(final Function<String, @Nullable String> lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    @Override
    public boolean isSet(final String name) {
        Objects.requireNonNull(name, "name");
        final boolean set = !name.isBlank() && isFilled(lookup.apply(name));
        log.debug("Environment variable name={} set={}", name, set);
        return set;
    }

    private static boolean isFilled(final @Nullable String value) {
        return value != null && !value.isBlank();
    }
}
