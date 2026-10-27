package ua.bookloom.app.env;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/** Runs one short reader command and returns what it printed; a seam, so a test never spawns a process. */
@FunctionalInterface
public interface CommandRunner {

    /**
     * Runs {@code command} with no shell.
     *
     * @param command the program and its arguments
     * @return its exit code and standard output; a command that cannot start, or outlives its time, throws an
     *     unchecked exception
     */
    Output run(List<String> command);

    /**
     * What a command printed.
     *
     * @param exitCode the process's exit code
     * @param stdout its standard output, decoded in the system's native encoding
     */
    record Output(int exitCode, String stdout) {

        /** Rejects a missing output. */
        public Output {
            Objects.requireNonNull(stdout, "stdout");
        }
    }

    /**
     * The real thing, bounded so a hung reader never holds the caller (which may be the FX thread) for long.
     *
     * @return a runner that ends a command after 1.5 seconds
     */
    static CommandRunner system() {
        return SystemRunner.INSTANCE;
    }

    /** The implementation behind {@link #system()}. */
    @Slf4j
    enum SystemRunner implements CommandRunner {
        /** The one instance. */
        INSTANCE;

        private static final Duration TIMEOUT = Duration.ofMillis(1500);

        @Override
        public Output run(final List<String> command) {
            Objects.requireNonNull(command, "command");
            log.debug("running reader command {}", command.getFirst());
            try {
                final Process process = new ProcessBuilder(command)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                // A reader prints a few lines, far below a pipe's buffer, so it can finish before its output is read.
                if (!process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    throw new UncheckedIOException(new IOException(command.getFirst() + " did not end in " + TIMEOUT));
                }
                final String out = new String(process.getInputStream().readAllBytes(), nativeCharset());
                return new Output(process.exitValue(), out);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while reading the environment", interrupted);
            }
        }

        private static Charset nativeCharset() {
            try {
                return Charset.forName(System.getProperty("native.encoding", "UTF-8"));
            } catch (IllegalArgumentException unknown) {
                return StandardCharsets.UTF_8;
            }
        }
    }
}
