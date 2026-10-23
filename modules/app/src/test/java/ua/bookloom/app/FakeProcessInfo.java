package ua.bookloom.app;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** A hand-written {@link ProcessHandle.Info}: only the command and its arguments are ever read. */
record FakeProcessInfo(Optional<String> command, Optional<String[]> arguments) implements ProcessHandle.Info {

    static FakeProcessInfo of(@Nullable final String command, @Nullable final String... arguments) {
        return new FakeProcessInfo(Optional.ofNullable(command), Optional.ofNullable(arguments));
    }

    static FakeProcessInfo withoutArguments(@Nullable final String command) {
        return new FakeProcessInfo(Optional.ofNullable(command), Optional.empty());
    }

    @Override
    public Optional<String> commandLine() {
        return Optional.empty();
    }

    @Override
    public Optional<Instant> startInstant() {
        return Optional.empty();
    }

    @Override
    public Optional<Duration> totalCpuDuration() {
        return Optional.empty();
    }

    @Override
    public Optional<String> user() {
        return Optional.empty();
    }
}
