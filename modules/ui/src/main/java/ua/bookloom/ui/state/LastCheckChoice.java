package ua.bookloom.ui.state;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.settings.AppSetting;

/**
 * Whether the last check before saving runs: the person's choice in this session, which starts from the saved
 * Automation setting when one is stored and else from the dial (on at Max, and following the dial while nothing is
 * stored), and is shown off and locked while the book is partial. The saved setting is only ever read here, never
 * written, so a partial book never changes it.
 *
 * <p>The stored value is read off the FX thread; until it lands the switch follows the dial. Touched on the FX thread
 * only, except the read itself.
 */
@Slf4j
final class LastCheckChoice {

    private final SettingsRepository settings;
    private final ExecutorService executor;
    private final BooleanProperty chosen = new SimpleBooleanProperty(false);
    private final ObjectProperty<@Nullable ExportFigures> figures = new SimpleObjectProperty<>();
    private final BooleanBinding locked;
    private final BooleanBinding shown;
    private @Nullable Boolean stored;
    private @Nullable QualityDial followedDial;
    private long epoch;

    LastCheckChoice(final SettingsRepository settings, final ExecutorService executor) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.executor = Objects.requireNonNull(executor, "executor");
        locked = Bindings.createBooleanBinding(
                () -> {
                    final ExportFigures read = figures.get();
                    return read != null && read.isPartial();
                },
                figures);
        shown = chosen.and(locked.not());
    }

    /** The person's choice, before the partial lock. */
    BooleanProperty chosen() {
        return chosen;
    }

    /** Whether the check is off and cannot be turned on, because the book is partial. */
    BooleanBinding locked() {
        return locked;
    }

    /** Whether the check runs if the book is exported now. */
    BooleanBinding shown() {
        return shown;
    }

    /** Takes the project's latest figures, which decide the partial lock. */
    void figures(final @Nullable ExportFigures read) {
        figures.set(read);
    }

    /** Takes the person's toggle. */
    void choose(final boolean on) {
        log.debug("last check chosen {} (locked {})", on, locked.get());
        chosen.set(on);
    }

    /** Follows the brief's dial; a stored choice wins over it. */
    void followBrief(final @Nullable BookBrief brief) {
        final QualityDial dial = brief == null ? null : brief.dial();
        if (dial == followedDial) {
            return;
        }
        followedDial = dial;
        log.debug("the dial is now {}; the last check follows it {}", dial, stored == null);
        if (stored == null) {
            chosen.set(dial == QualityDial.MAX);
        }
    }

    /** Reads the saved choice again, as a newly opened book starts from it. */
    void reload() {
        epoch++;
        final long asked = epoch;
        try {
            executor.execute(() -> read(asked));
        } catch (RejectedExecutionException rejected) {
            log.warn("the saved last-check choice could not be read; the dial decides", rejected);
        }
    }

    private void read(final long asked) {
        final Map<String, String> all = settings.all().data();
        final String raw = all == null ? null : all.get(AppSetting.LAST_CHECK.key());
        final Boolean value =
                raw == null ? null : AppSetting.LAST_CHECK.parser().apply(raw).orElse(null);
        Platform.runLater(() -> apply(asked, value));
    }

    private void apply(final long asked, final @Nullable Boolean value) {
        if (asked != epoch) {
            log.debug("saved last-check choice {} discarded: question {} is current", asked, epoch);
            return;
        }
        stored = value;
        log.debug("last check starts from {}: {}", value == null ? "the dial" : "the saved choice", value);
        chosen.set(value != null ? value : followedDial == QualityDial.MAX);
    }
}
