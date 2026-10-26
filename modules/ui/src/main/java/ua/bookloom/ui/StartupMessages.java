package ua.bookloom.ui;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.i18n.OsLocaleProvider;

/**
 * The words of a failure that stops the window before it exists. No stored language can be read then (the database is
 * what failed), so the operating system's language chooses, as it does on a first launch.
 */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StartupMessages {

    /** The safe detail the database gives a file another program holds. */
    private static final String LOCKED_DETAIL = "storage open locked";

    /**
     * The database's failure in the person's words.
     *
     * @param failure what the database reported; its code, details and cause are kept for the log
     * @param dataDir the data folder, named so the person knows where to look
     * @param os the operating system's locale
     * @return the same failure with a translated title and message
     */
    public static AppError storageFailure(final AppError failure, final Path dataDir, final Locale os) {
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(dataDir, "dataDir");
        Objects.requireNonNull(os, "os");
        final Messages messages = new Messages(() -> OsLocaleProvider.forOs(os));
        final MessageKey text = LOCKED_DETAIL.equals(failure.details())
                ? MessageKey.STARTUP_STORAGE_IN_USE
                : MessageKey.STARTUP_STORAGE_NOT_WRITABLE;
        log.debug(
                "startup storage failure worded: text={} language={}",
                text,
                messages.locale().getLanguage());
        return new AppError(
                failure.code(),
                messages.get(MessageKey.STARTUP_STORAGE_TITLE),
                messages.get(text, dataDir.toString()),
                failure.details(),
                failure.retryable(),
                failure.cause());
    }
}
