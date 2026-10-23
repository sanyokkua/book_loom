package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.api.settings.ReviewModeChoice;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * The Automation tab's stored choices: when to stop and ask, the quality new books start at, the review desk, the
 * last-check default, keep-awake and reopening the last project. A choice is stored the moment it is made; the review
 * mode reaches the next run that starts, the rest apply at once. FX Application Thread only, except the reads and
 * writes it hands to the background executor.
 */
@Slf4j
@Singleton
public final class AutomationSettings {

    /** The choices a person can make here, to tell which one was made before the stored values arrived. */
    private enum Choice {
        REVIEW_MODE,
        DIAL,
        OPEN_DESK,
        LAST_CHECK,
        KEEP_AWAKE,
        REOPEN
    }

    private final SettingsRepository settings;
    private final ReviewModeChoice reviewModeChoice;
    private final KeepAwakeGate gate;
    private final Executor background;
    private final Executor fx;
    private final ObjectProperty<ReviewMode> reviewMode = new SimpleObjectProperty<>(ReviewMode.UNATTENDED);
    private final ObjectProperty<QualityDial> defaultDial = new SimpleObjectProperty<>(QualityDial.BALANCED);
    private final BooleanProperty openReviewDesk = new SimpleBooleanProperty(true);
    private final BooleanProperty lastCheck = new SimpleBooleanProperty(false);
    private final BooleanProperty lastCheckChosen = new SimpleBooleanProperty(false);
    private final BooleanProperty keepAwake = new SimpleBooleanProperty(true);
    private final BooleanProperty reopenLastProject = new SimpleBooleanProperty(true);
    private final StringProperty reviewModeOverride = new SimpleStringProperty();
    private final BooleanProperty loaded = new SimpleBooleanProperty(false);
    private final BooleanProperty saveFailed = new SimpleBooleanProperty(false);
    private final Set<Choice> chosen = EnumSet.noneOf(Choice.class);
    private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);

    /**
     * Creates the holder over the real store, pool and FX thread.
     *
     * @param settings where the choices are kept
     * @param reviewModeChoice tells the run's source about a new review mode and names a launch override
     * @param gate the keep-awake choice the computer is held by
     * @param background where the store is read and written, never the FX thread
     */
    @Inject
    public AutomationSettings(
            final SettingsRepository settings,
            final ReviewModeChoice reviewModeChoice,
            final KeepAwakeGate gate,
            final @BackgroundExecutor ExecutorService background) {
        this(settings, reviewModeChoice, gate, background, Platform::runLater);
    }

    /**
     * The same, with the hop back to the FX thread supplied, so a test needs no toolkit.
     *
     * @param settings where the choices are kept
     * @param reviewModeChoice tells the run's source about a new review mode and names a launch override
     * @param gate the keep-awake choice the computer is held by
     * @param background where the store is read and written
     * @param fx runs a task on the FX Application Thread
     */
    public AutomationSettings(
            final SettingsRepository settings,
            final ReviewModeChoice reviewModeChoice,
            final KeepAwakeGate gate,
            final Executor background,
            final Executor fx) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.reviewModeChoice = Objects.requireNonNull(reviewModeChoice, "reviewModeChoice");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.background = Objects.requireNonNull(background, "background");
        this.fx = Objects.requireNonNull(fx, "fx");
    }

    /** Reads the stored choices and the launch override in the background and publishes them on the FX thread. */
    public void load() {
        log.debug("loading the automation settings");
        background.execute(() -> {
            final Stored stored = new Stored(
                    read(AppSetting.REVIEW_MODE),
                    read(AppSetting.DEFAULT_DIAL),
                    read(AppSetting.OPEN_REVIEW_DESK),
                    read(AppSetting.LAST_CHECK),
                    isWritten(AppSetting.LAST_CHECK),
                    read(AppSetting.KEEP_AWAKE),
                    read(AppSetting.REOPEN_LAST_PROJECT),
                    reviewModeChoice.launchOverride().orElse(null));
            fx.execute(() -> publish(stored));
        });
    }

    /** What the store holds, read off the FX thread and published together. */
    private record Stored(
            ReviewMode reviewMode,
            QualityDial dial,
            boolean openDesk,
            boolean lastCheck,
            boolean lastCheckWritten,
            boolean keepAwake,
            boolean reopen,
            @Nullable String override) {}

    private <T> T read(final AppSetting<T> setting) {
        final T fallback = Objects.requireNonNull(setting.defaultValue(), "default");
        final Optional<T> value = settings.get(setting).data();
        if (value == null) {
            log.warn("automation: {} could not be read; showing its default", setting.key());
            return fallback;
        }
        return value.orElse(fallback);
    }

    private boolean isWritten(final AppSetting<?> setting) {
        final Map<String, String> all = settings.all().data();
        if (all == null) {
            log.warn("automation: the stored keys could not be listed; {} counts as not written", setting.key());
            return false;
        }
        return all.containsKey(setting.key());
    }

    // A choice made before the stored values arrived wins over them.
    private void publish(final Stored stored) {
        setUnlessChosen(Choice.REVIEW_MODE, reviewMode, stored.reviewMode());
        setUnlessChosen(Choice.DIAL, defaultDial, stored.dial());
        setUnlessChosen(Choice.OPEN_DESK, openReviewDesk, stored.openDesk());
        setUnlessChosen(Choice.LAST_CHECK, lastCheck, stored.lastCheck());
        if (!chosen.contains(Choice.LAST_CHECK)) {
            lastCheckChosen.set(stored.lastCheckWritten());
        }
        setUnlessChosen(Choice.KEEP_AWAKE, keepAwake, stored.keepAwake());
        setUnlessChosen(Choice.REOPEN, reopenLastProject, stored.reopen());
        reviewModeOverride.set(stored.override());
        loaded.set(true);
        log.debug("automation loaded {}", stored);
    }

    private <T> void setUnlessChosen(final Choice choice, final Property<T> target, final T value) {
        if (!chosen.contains(choice)) {
            target.setValue(value);
        }
    }

    /**
     * Holds and stores when to stop and ask; the next run that starts reads it.
     *
     * @param mode the non-null mode
     */
    public void chooseReviewMode(final ReviewMode mode) {
        Objects.requireNonNull(mode, "mode");
        log.debug("review mode choice {} -> {}", reviewMode.get(), mode);
        hold(Choice.REVIEW_MODE);
        reviewMode.set(mode);
        storeThen(AppSetting.REVIEW_MODE, mode, () -> reviewModeChoice.stored(mode));
    }

    /**
     * Holds and stores the quality new books start at.
     *
     * @param dial the non-null quality
     */
    public void chooseDial(final QualityDial dial) {
        Objects.requireNonNull(dial, "dial");
        log.debug("default quality choice {} -> {}", defaultDial.get(), dial);
        hold(Choice.DIAL);
        defaultDial.set(dial);
        store(AppSetting.DEFAULT_DIAL, dial);
    }

    /**
     * Holds and stores whether the review desk opens by itself when a run ends with segments to review.
     *
     * @param on the choice
     */
    public void chooseOpenReviewDesk(final boolean on) {
        log.debug("open review desk choice {} -> {}", openReviewDesk.get(), on);
        hold(Choice.OPEN_DESK);
        openReviewDesk.set(on);
        store(AppSetting.OPEN_REVIEW_DESK, on);
    }

    /**
     * Holds and stores the last-check default; from now on it is a written choice, not the follow-the-quality default.
     *
     * @param on the choice
     */
    public void chooseLastCheck(final boolean on) {
        log.debug("last check default choice {} -> {}", lastCheck.get(), on);
        hold(Choice.LAST_CHECK);
        lastCheck.set(on);
        lastCheckChosen.set(true);
        store(AppSetting.LAST_CHECK, on);
    }

    /**
     * Holds and stores keep-awake; a computer held awake is let go at once when this turns it off.
     *
     * @param on the choice
     */
    public void chooseKeepAwake(final boolean on) {
        log.debug("keep awake choice {} -> {}", keepAwake.get(), on);
        hold(Choice.KEEP_AWAKE);
        keepAwake.set(on);
        gate.set(on);
        store(AppSetting.KEEP_AWAKE, on);
    }

    /**
     * Holds and stores whether the window reopens the last project at launch.
     *
     * @param on the choice
     */
    public void chooseReopenLastProject(final boolean on) {
        log.debug("reopen last project choice {} -> {}", reopenLastProject.get(), on);
        hold(Choice.REOPEN);
        reopenLastProject.set(on);
        store(AppSetting.REOPEN_LAST_PROJECT, on);
    }

    private void hold(final Choice choice) {
        chosen.add(choice);
    }

    private <T> void store(final AppSetting<T> setting, final T value) {
        storeThen(setting, value, () -> {});
    }

    // Each write waits for the one before it, so a quick second choice is never overwritten by the first.
    private <T> void storeThen(final AppSetting<T> setting, final T value, final Runnable afterStored) {
        lastWrite = lastWrite
                .handle((done, failure) -> (Void) null)
                .thenRunAsync(() -> write(setting, value, afterStored), background);
    }

    private <T> void write(final AppSetting<T> setting, final T value, final Runnable afterStored) {
        boolean failed;
        try {
            failed = settings.put(setting, value).isErr();
            if (!failed) {
                afterStored.run();
            }
        } catch (RuntimeException e) {
            log.error("storing {} failed", setting.key(), e);
            failed = true;
        }
        if (failed) {
            log.warn("automation: {} could not be stored; it holds until the app closes", setting.key());
        }
        final boolean shown = failed;
        fx.execute(() -> saveFailed.set(shown));
    }

    /** {@return the review mode; the stored one once {@link #load} has arrived} */
    public ObjectProperty<ReviewMode> reviewModeProperty() {
        return reviewMode;
    }

    /** {@return the quality new books start at} */
    public ObjectProperty<QualityDial> defaultDialProperty() {
        return defaultDial;
    }

    /** {@return whether the review desk opens by itself when a run ends with segments to review} */
    public BooleanProperty openReviewDeskProperty() {
        return openReviewDesk;
    }

    /** {@return the last-check default, off while it has not been chosen} */
    public BooleanProperty lastCheckProperty() {
        return lastCheck;
    }

    /** {@return whether the last-check default has been written, so it no longer follows the book's quality} */
    public ReadOnlyBooleanProperty lastCheckChosenProperty() {
        return lastCheckChosen;
    }

    /** {@return whether the computer is kept awake while a model works} */
    public BooleanProperty keepAwakeProperty() {
        return keepAwake;
    }

    /** {@return whether the window reopens the last project at launch} */
    public BooleanProperty reopenLastProjectProperty() {
        return reopenLastProject;
    }

    /** {@return the launch variable or property that decides the review mode, or {@code null} when none does} */
    public ReadOnlyStringProperty reviewModeOverrideProperty() {
        return reviewModeOverride;
    }

    /** {@return whether the stored choices have arrived} */
    public ReadOnlyBooleanProperty loadedProperty() {
        return loaded;
    }

    /** {@return whether the last choice could not be stored; it still holds until the app closes} */
    public ReadOnlyBooleanProperty saveFailedProperty() {
        return saveFailed;
    }

    /** {@return the chain of writes made so far, for a test to wait on} */
    CompletableFuture<Void> lastWrite() {
        return lastWrite;
    }
}
