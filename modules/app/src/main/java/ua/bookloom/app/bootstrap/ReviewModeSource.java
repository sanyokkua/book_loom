package ua.bookloom.app.bootstrap;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.app.StartupContext;

/**
 * Resolves the review mode a run starts with: the launch flag when one was set on purpose, else the stored
 * "When to stop and ask you" setting, else Unattended.
 */
@Singleton
public final class ReviewModeSource {

    private final StartupContext startup;
    private final SettingsRepository settings;
    private final AtomicReference<ReviewMode> lastStored = new AtomicReference<>(ReviewMode.UNATTENDED);

    /**
     * Creates the source over the launch flag and the stored settings.
     *
     * @param startup what the launcher resolved, including the review-mode flag
     * @param settings where the Automation setting is kept
     */
    @Inject
    public ReviewModeSource(final StartupContext startup, final SettingsRepository settings) {
        this.startup = Objects.requireNonNull(startup, "startup");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Reads the mode now; an off-thread read also remembers the stored choice for FX-thread callers, and startup makes
     * one such read after the database is open, so a change in Settings reaches the next run.
     *
     * @return the mode for the run about to start (on the FX thread, the stored choice as last read elsewhere); a launch flag that was set, even to a value naming no mode, wins
     *     over the stored choice, and an unreadable or empty store gives Unattended
     */
    public ReviewMode current() {
        // A local logger: this package is on the bootstrap path, where no static logger may exist (logging.md).
        final Logger log = LoggerFactory.getLogger(ReviewModeSource.class);
        final ResolvedReviewMode flag = startup.reviewMode();
        if (flag.source() != ResolvedReviewMode.Source.DEFAULT || flag.rejectedValue() != null) {
            log.debug("review mode {} from {}", flag.mode(), flagOrigin(flag));
            return flag.mode();
        }
        if (Platform.isFxApplicationThread()) {
            // The store is never read on the FX thread (SqliteDatabase's guard); the last off-thread read stands in.
            final ReviewMode known = Objects.requireNonNull(lastStored.get(), "lastStored");
            log.debug("review mode {} from stored (last known, not read on the FX thread)", known);
            return known;
        }
        final Result<Optional<ReviewMode>> stored = settings.get(AppSetting.REVIEW_MODE);
        if (stored.isErr()) {
            log.warn("review mode setting unreadable, using {}", ReviewMode.UNATTENDED);
            return ReviewMode.UNATTENDED;
        }
        final Optional<ReviewMode> value = Objects.requireNonNull(stored.data(), "data");
        lastStored.set(value.orElse(ReviewMode.UNATTENDED));
        log.debug(
                "review mode {} from {}",
                value.orElse(ReviewMode.UNATTENDED),
                value.isPresent() ? "stored" : "default");
        return value.orElse(ReviewMode.UNATTENDED);
    }

    private static String flagOrigin(final ResolvedReviewMode flag) {
        return switch (flag.source()) {
            case ENVIRONMENT -> "environment";
            case PROPERTY -> "property";
            case DEFAULT -> "environment (value named no mode)";
        };
    }
}
