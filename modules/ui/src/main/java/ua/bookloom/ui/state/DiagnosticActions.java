package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.BuildVersion;
import ua.bookloom.ui.DiagnosticLog;
import ua.bookloom.ui.SessionInfo;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.notify.Toasts;

/**
 * What the About dialog's diagnostic actions do: show the log folder, copy its path, and save a diagnostic bundle.
 *
 * <p>Every action is local. The folder is opened by the operating system's file manager, the path goes to the
 * clipboard, and the bundle is written to the file the person picks — the person decides whether to share it.
 * Writing the bundle reads and compresses up to 100 MB, so it runs on the background executor and only its outcome
 * comes back to the FX thread.
 */
@Slf4j
@Singleton
public final class DiagnosticActions {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private final DiagnosticLog diagnosticLog;
    private final SessionInfo session;
    private final FileRevealer revealer;
    private final DestinationChooser chooser;
    private final Toasts toasts;
    private final ErrorPresenter errors;
    private final String version;
    private final ExecutorService executor;
    private final Clock clock;
    private final Consumer<String> clipboard;

    /**
     * Creates the actions over the system clipboard and clock.
     *
     * @param diagnosticLog where the logs are and whether the detailed log is on
     * @param session the facts {@code session.json} names
     * @param revealer opens the log folder in the file manager
     * @param chooser asks where the bundle goes
     * @param toasts says that a bundle was saved or a path copied
     * @param errors shows why a bundle could not be saved
     * @param version the build version {@code session.json} names
     * @param executor the daemon pool the bundle is written on
     */
    @Inject
    public DiagnosticActions(
            final DiagnosticLog diagnosticLog,
            final SessionInfo session,
            final FileRevealer revealer,
            final DestinationChooser chooser,
            final Toasts toasts,
            final ErrorPresenter errors,
            @BuildVersion final String version,
            @BackgroundExecutor final ExecutorService executor) {
        this(
                diagnosticLog,
                session,
                revealer,
                chooser,
                toasts,
                errors,
                version,
                executor,
                Clock.system(ZoneId.systemDefault()),
                DiagnosticActions::copyToSystemClipboard);
    }

    DiagnosticActions(
            final DiagnosticLog diagnosticLog,
            final SessionInfo session,
            final FileRevealer revealer,
            final DestinationChooser chooser,
            final Toasts toasts,
            final ErrorPresenter errors,
            final String version,
            final ExecutorService executor,
            final Clock clock,
            final Consumer<String> clipboard) {
        this.diagnosticLog = Objects.requireNonNull(diagnosticLog, "diagnosticLog");
        this.session = Objects.requireNonNull(session, "session");
        this.revealer = Objects.requireNonNull(revealer, "revealer");
        this.chooser = Objects.requireNonNull(chooser, "chooser");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.version = Objects.requireNonNull(version, "version");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
    }

    /**
     * Where the logs are and whether the detailed one is written.
     *
     * @return the launch's log settings
     */
    public DiagnosticLog diagnosticLog() {
        return diagnosticLog;
    }

    /** Opens the log folder in the operating system's file manager. FX thread only. */
    public void openFolder() {
        log.info("opening the log folder {}", diagnosticLog.directory());
        revealer.open(diagnosticLog.directory());
    }

    /** Puts the log folder's path on the clipboard and says so. FX thread only. */
    public void copyPath() {
        log.info("copying the log folder path to the clipboard");
        clipboard.accept(diagnosticLog.directory().toString());
        toasts.info(MessageKey.ABOUT_LOG_PATH_COPIED);
    }

    /** Asks where the bundle goes and writes it there in the background; a cancelled dialog does nothing. */
    public void saveBundle() {
        final Path initial = Path.of(System.getProperty("user.home", "."))
                .resolve("bookloom-diagnostics-" + LocalDateTime.now(clock).format(STAMP) + ".zip");
        final Optional<Path> chosen = chooser.choose(initial);
        log.info("diagnostic bundle requested; a file was chosen {}", chosen.isPresent());
        chosen.ifPresent(this::writeInBackground);
    }

    private void writeInBackground(final Path target) {
        final String json = DiagnosticBundle.sessionJson(about(), session.snapshot());
        try {
            executor.execute(() -> {
                final Result<Integer> written = DiagnosticBundle.write(target, diagnosticLog.directory(), json);
                Platform.runLater(() -> report(target, written));
            });
        } catch (RejectedExecutionException stopped) {
            log.warn("the diagnostic bundle could not be started: the background pool has stopped", stopped);
            errors.present(AppError.of(
                    ErrorCode.internal,
                    "Diagnostic bundle not saved",
                    "The diagnostic bundle could not be started.",
                    null,
                    stopped));
        }
    }

    private void report(final Path target, final Result<Integer> written) {
        final AppError error = written.error();
        if (error != null) {
            errors.present(error);
            return;
        }
        final Path name = target.getFileName();
        toasts.success(MessageKey.ABOUT_BUNDLE_SAVED, name == null ? target.toString() : name.toString());
    }

    private Map<String, String> about() {
        final Map<String, String> about = new LinkedHashMap<>();
        about.put("app", "BookLoom");
        about.put("version", version);
        about.put("os", property("os.name") + " " + property("os.version") + " " + property("os.arch"));
        about.put("jvm", property("java.vm.name") + " " + property("java.runtime.version"));
        about.put("javafx", property("javafx.runtime.version"));
        about.put("locale", Locale.getDefault().toLanguageTag());
        about.put("detailedLog", diagnosticLog.detailed() ? "on" : "off");
        return about;
    }

    private static String property(final String name) {
        return System.getProperty(name, "unknown");
    }

    private static void copyToSystemClipboard(final String text) {
        final ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
