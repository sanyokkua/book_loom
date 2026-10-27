package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
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
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.api.settings.ReviewModeChoice;
import ua.bookloom.ui.SettingWriter;

/**
 * The Automation tab's stored choices: when to stop and ask, the quality new books start at, the review desk, the
 * last-check default, keep-awake and reopening the last project. A choice is stored the moment it is made; the review
 * mode reaches the next run that starts, the rest apply at once. FX Application Thread only, except the reads and
 * writes it hands to its {@link SettingWriter}.
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

    private final SettingWriter writer;
    private final ReviewModeChoice reviewModeChoice;
    private final KeepAwakeGate gate;
    private final ObjectProperty<ReviewMode> reviewMode = new SimpleObjectProperty<>(ReviewMode.UNATTENDED);
    private final ObjectProperty<QualityDial> defaultDial = new SimpleObjectProperty<>(QualityDial.BALANCED);
    private final BooleanProperty openReviewDesk = new SimpleBooleanProperty(true);
    private final BooleanProperty lastCheck = new SimpleBooleanProperty(false);
    private final BooleanProperty lastCheckChosen = new SimpleBooleanProperty(false);
    private final BooleanProperty keepAwake = new SimpleBooleanProperty(true);
    private final BooleanProperty reopenLastProject = new SimpleBooleanProperty(true);
    private final StringProperty reviewModeOverride = new SimpleStringProperty();
    private final BooleanProperty loaded = new SimpleBooleanProperty(false);
    private final Set<Choice> chosen = EnumSet.noneOf(Choice.class);

    /**
     * Creates the holder.
     *
     * @param writer reads and stores the choices off the FX thread
     * @param reviewModeChoice tells the run's source about a new review mode and names a launch override
     * @param gate the keep-awake choice the computer is held by
     */
    @Inject
    public AutomationSettings(
            final SettingWriter writer, final ReviewModeChoice reviewModeChoice, final KeepAwakeGate gate) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.reviewModeChoice = Objects.requireNonNull(reviewModeChoice, "reviewModeChoice");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    /** Reads the stored choices and the launch override in the background and publishes them on the FX thread. */
    public void load() {
        log.debug("loading the automation settings");
        writer.load(
                () -> new Stored(
                        writer.read(AppSetting.REVIEW_MODE),
                        writer.read(AppSetting.DEFAULT_DIAL),
                        writer.read(AppSetting.OPEN_REVIEW_DESK),
                        writer.read(AppSetting.LAST_CHECK),
                        writer.isWritten(AppSetting.LAST_CHECK),
                        writer.read(AppSetting.KEEP_AWAKE),
                        writer.read(AppSetting.REOPEN_LAST_PROJECT),
                        reviewModeChoice.launchOverride().orElse(null)),
                this::publish);
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
        writer.put(AppSetting.REVIEW_MODE, mode, () -> reviewModeChoice.stored(mode));
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
        writer.put(AppSetting.DEFAULT_DIAL, dial);
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
        writer.put(AppSetting.OPEN_REVIEW_DESK, on);
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
        writer.put(AppSetting.LAST_CHECK, on);
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
        writer.put(AppSetting.KEEP_AWAKE, on);
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
        writer.put(AppSetting.REOPEN_LAST_PROJECT, on);
    }

    private void hold(final Choice choice) {
        chosen.add(choice);
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
        return writer.saveFailedProperty();
    }
}
