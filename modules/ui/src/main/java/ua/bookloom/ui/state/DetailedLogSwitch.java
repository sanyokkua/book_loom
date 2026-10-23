package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.DiagnosticLog;

/**
 * The Storage tab's detailed diagnostic log switch. It is a marker file in the data folder that the next launch reads;
 * nothing about the log running now changes. The file is read, written and removed on the background executor.
 */
@Slf4j
@Singleton
public final class DetailedLogSwitch {

    private static final String MARKER_TEXT =
            "Written by Settings › Storage & logs; delete to switch the detailed log off.\n";

    private final DiagnosticLog diagnosticLog;
    private final Executor background;
    private final Executor fx;
    private final BooleanProperty on = new SimpleBooleanProperty(false);
    private final BooleanProperty loaded = new SimpleBooleanProperty(false);
    private final BooleanProperty saveFailed = new SimpleBooleanProperty(false);
    private boolean chosen;
    private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);

    /**
     * Creates the switch over the real pool and FX thread.
     *
     * @param diagnosticLog names the marker and what this launch's log is
     * @param background where the marker is read and changed, never the FX thread
     */
    @Inject
    public DetailedLogSwitch(final DiagnosticLog diagnosticLog, final @BackgroundExecutor ExecutorService background) {
        this(diagnosticLog, background, Platform::runLater);
    }

    /**
     * The same, with the hop back to the FX thread supplied, so a test needs no toolkit.
     *
     * @param diagnosticLog names the marker and what this launch's log is
     * @param background where the marker is read and changed
     * @param fx runs a task on the FX Application Thread
     */
    public DetailedLogSwitch(final DiagnosticLog diagnosticLog, final Executor background, final Executor fx) {
        this.diagnosticLog = Objects.requireNonNull(diagnosticLog, "diagnosticLog");
        this.background = Objects.requireNonNull(background, "background");
        this.fx = Objects.requireNonNull(fx, "fx");
    }

    /** Reads whether the marker is there in the background and publishes it on the FX thread. */
    public void load() {
        log.debug("reading the detailed log marker");
        background.execute(() -> {
            final boolean present = Files.exists(diagnosticLog.marker());
            fx.execute(() -> publish(present));
        });
    }

    // A read that began before the person's choice must not undo it.
    private void publish(final boolean present) {
        if (chosen) {
            log.debug("detailed log marker read ignored: a choice was made meanwhile");
        } else {
            on.set(present);
        }
        loaded.set(true);
        log.debug("detailed log marker present={}", present);
    }

    /**
     * Switches the log on or off from the next launch by creating or removing the marker.
     *
     * @param wanted whether the next launch writes the detailed log
     */
    public void choose(final boolean wanted) {
        log.debug("detailed log choice {} -> {}", on.get(), wanted);
        chosen = true;
        on.set(wanted);
        // Each change waits for the one before it, so the marker always follows the last choice.
        lastWrite = lastWrite
                .handle((done, failure) -> (Void) null)
                .thenRunAsync(
                        () -> {
                            final boolean failed = !apply(wanted);
                            fx.execute(() -> saveFailed.set(failed));
                        },
                        background);
    }

    private boolean apply(final boolean wanted) {
        final Path marker = diagnosticLog.marker();
        try {
            if (wanted) {
                Files.writeString(marker, MARKER_TEXT, StandardOpenOption.CREATE_NEW);
            } else {
                Files.deleteIfExists(marker);
            }
            log.info("detailed log marker {}", wanted ? "created" : "removed");
            return true;
        } catch (FileAlreadyExistsException alreadyThere) {
            log.debug("detailed log marker already exists");
            return true;
        } catch (IOException e) {
            log.warn(
                    "the detailed log marker could not be {}; the switch holds until the app closes",
                    wanted ? "created" : "removed",
                    e);
            return false;
        }
    }

    /** {@return whether this launch writes the detailed log} */
    public boolean runningNow() {
        return diagnosticLog.detailed();
    }

    /** {@return the launch variable or property that decided this launch's log, or {@code null} when none did} */
    public @Nullable String decidedBy() {
        return diagnosticLog.decidedBy();
    }

    /** {@return whether a development run writes the log whatever the switch says} */
    public boolean developmentAlways() {
        return diagnosticLog.developmentDefault() && !on.get();
    }

    /** {@return whether the next launch writes the detailed log, as far as the marker says} */
    public BooleanProperty onProperty() {
        return on;
    }

    /** {@return whether the marker has been read} */
    public ReadOnlyBooleanProperty loadedProperty() {
        return loaded;
    }

    /** {@return whether the last change could not be written; the switch still holds until the app closes} */
    public ReadOnlyBooleanProperty saveFailedProperty() {
        return saveFailed;
    }
}
