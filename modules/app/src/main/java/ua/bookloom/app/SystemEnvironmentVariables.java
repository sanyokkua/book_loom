package ua.bookloom.app;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.EnvironmentVariables;
import ua.bookloom.api.llm.ProviderConfigRules;
import ua.bookloom.app.env.CommandRunner;
import ua.bookloom.app.env.OsEnvironment;

/**
 * The one lookup of an environment variable, for both the request headers (the value, at call time) and the
 * Providers form (only whether it is set): the process environment first, then the operating system's own store of
 * the user's variables, because an app opened from the Dock, a file manager or an IDE never sees a shell's
 * {@code export} lines. A variable set after BookLoom started is seen within {@link #CACHE_TIME}, without a restart.
 * A value is never logged, kept beyond that short cache, or stored; a store that cannot be read counts as "not set".
 */
@Slf4j
final class SystemEnvironmentVariables implements EnvironmentVariables {

    /** How long an answer of the operating system's store is reused, so the form's every keystroke spawns nothing. */
    static final Duration CACHE_TIME = Duration.ofSeconds(2);

    private final Function<String, @Nullable String> process;
    private final OsEnvironment os;
    private final LongSupplier nanoTime;
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private final AtomicBoolean warned = new AtomicBoolean();

    SystemEnvironmentVariables() {
        this(
                System::getenv,
                OsEnvironment.forOs(
                        System.getProperty("os.name", ""), System::getenv, System::getProperty, CommandRunner.system()),
                System::nanoTime);
    }

    SystemEnvironmentVariables(
            final Function<String, @Nullable String> process, final OsEnvironment os, final LongSupplier nanoTime) {
        this.process = Objects.requireNonNull(process, "process");
        this.os = Objects.requireNonNull(os, "os");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    @Override
    public boolean isSet(final String name) {
        return value(name) != null;
    }

    /**
     * The variable's value, for a request header only.
     *
     * @param name the variable's name; anything but letters, digits and underscores (not starting with a digit) is
     *     never looked up
     * @return the non-blank value, or {@code null} when it is unset, blank, unreadable or the name is not a name
     */
    @Nullable
    String value(final String name) {
        Objects.requireNonNull(name, "name");
        if (!ProviderConfigRules.ENV_NAME.matcher(name).matches()) {
            log.debug("Environment variable not looked up: not a variable name length={}", name.length());
            return null;
        }
        final String fromProcess = process.apply(name);
        if (fromProcess != null && !fromProcess.isBlank()) {
            log.debug("Environment variable name={} source=process", name);
            return fromProcess;
        }
        final String fromOs = fromOs(name);
        log.debug("Environment variable name={} source={}", name, fromOs == null ? "none" : os.source());
        return fromOs;
    }

    private @Nullable String fromOs(final String name) {
        final long now = nanoTime.getAsLong();
        final Answer cached = answers.get(name);
        if (cached != null && now - cached.readAt() < CACHE_TIME.toNanos()) {
            log.trace("Environment variable name={} answered from the {} cache", name, os.source());
            return cached.value();
        }
        final String value = readOs(name);
        answers.values().removeIf(old -> now - old.readAt() >= CACHE_TIME.toNanos());
        answers.put(name, new Answer(value, now));
        return value;
    }

    private @Nullable String readOs(final String name) {
        try {
            return os.read(name).filter(value -> !value.isBlank()).orElse(null);
        } catch (RuntimeException failure) {
            // Once per launch: the form asks on every keystroke, and a broken store is the same story each time.
            if (warned.compareAndSet(false, true)) {
                log.warn(
                        "The {} store of environment variables could not be read; treated as not set",
                        os.source(),
                        failure);
            } else {
                log.debug("The {} store could not be read again name={}", os.source(), name);
            }
            return null;
        }
    }

    /** One answer of the operating system's store and when it was read. */
    private record Answer(@Nullable String value, long readAt) {

        @Override
        public String toString() {
            return "Answer[set=" + Optional.ofNullable(value).isPresent() + ", readAt=" + readAt + "]";
        }
    }
}
