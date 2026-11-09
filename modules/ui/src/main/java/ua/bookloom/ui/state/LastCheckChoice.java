package ua.bookloom.ui.state;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;

/**
 * Whether the switch of the last check, which no longer runs, is shown on: the person's choice in this session, which
 * follows the dial (on at Max) and is shown off and locked while the book is partial. Nothing is saved or sent on; task
 * 14.6 removes the switch with the screen's other last-check parts. Touched on the FX thread only.
 */
@Slf4j
final class LastCheckChoice {

    private final BooleanProperty chosen = new SimpleBooleanProperty(false);
    private final ObjectProperty<@Nullable ExportFigures> figures = new SimpleObjectProperty<>();
    private final BooleanBinding locked;
    private final BooleanBinding shown;
    private @Nullable QualityDial followedDial;

    LastCheckChoice() {
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
        log.debug("the dial is now {}; the last check follows it", dial);
        chosen.set(dial == QualityDial.MAX);
    }

    /** Starts again from the dial, as a newly opened book does; no saved choice exists any more. */
    void reload() {
        log.debug("last check starts from the dial: {}", followedDial);
        chosen.set(followedDial == QualityDial.MAX);
    }
}
