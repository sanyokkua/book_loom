package ua.bookloom.app;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

/** A {@link Processes} that spawns nothing: it records the command into a shared event list and answers as told. */
final class RecordingProcesses implements Processes {

    private final List<String> events;
    private final boolean succeeds;

    RecordingProcesses(final List<String> events, final boolean succeeds) {
        this.events = events;
        this.succeeds = succeeds;
    }

    @Override
    public Optional<Process> start(final List<String> command) {
        events.add("start:" + String.join(" ", command));
        return succeeds ? Optional.of(new StubProcess()) : Optional.empty();
    }

    /** A process that is already gone. */
    private static final class StubProcess extends Process {

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            // nothing runs
        }
    }
}
