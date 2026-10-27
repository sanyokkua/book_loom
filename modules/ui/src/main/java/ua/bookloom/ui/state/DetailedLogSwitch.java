package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.DiagnosticLog;
import ua.bookloom.ui.SettingWriter;

/**
 * The Storage tab's detailed diagnostic log switch. It is a marker file in the data folder that the next launch reads;
 * nothing about the log running now changes. The file is read, written and removed off the FX thread.
 */
@Slf4j
@Singleton
public final class DetailedLogSwitch {

    private static final String MARKER_TEXT =
            "Written by Settings › Storage & logs; delete to switch the detailed log off.\n";

    private final DiagnosticLog diagnosticLog;
    private final SettingWriter writer;
    private final BooleanProperty on = new SimpleBooleanProperty(false);
    private final BooleanProperty loaded = new SimpleBooleanProperty(false);
    private boolean chosen;

    /**
     * Creates the switch.
     *
     * @param diagnosticLog names the marker and what this launch's log is
     * @param writer reads and changes the marker off the FX thread, in the order the choices were made
     */
    @Inject
    public DetailedLogSwitch(final DiagnosticLog diagnosticLog, final SettingWriter writer) {
        this.diagnosticLog = Objects.requireNonNull(diagnosticLog, "diagnosticLog");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    /** Reads whether the marker is there in the background and publishes it on the FX thread. */
    public void load() {
        log.debug("reading the detailed log marker");
        writer.load(() -> Files.exists(diagnosticLog.marker()), this::publish);
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
        writer.write("the detailed log marker", () -> apply(wanted));
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
            log.warn("the detailed log marker could not be {}", wanted ? "created" : "removed", e);
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
        return writer.saveFailedProperty();
    }
}
