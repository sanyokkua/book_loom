package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.NamesEstimate;
import ua.bookloom.api.pipeline.NamesPass;

/**
 * What the automatic pass would cost for the open project's dial, asked of the port again whenever the screen opens,
 * the dial changes or a stored speed changes (a names pass or a run stores the seconds it measured once it ends, so the
 * card turns from "estimate" to "measured on this model" without leaving the screen). A refused question leaves the
 * estimate empty rather than the last one, so the button never promises minutes nobody computed. FX thread only.
 */
@Slf4j
final class AutoEstimate {

    private final NamesPass namesPass;
    private final GlossaryCalls calls;
    private final SettingsViewModel settings;
    private final MeasuredCalls measured;
    private final ReadOnlyObjectWrapper<@Nullable NamesEstimate> estimate = new ReadOnlyObjectWrapper<>();
    // The last question asked, asked again when a stored speed changes; null before the screen first asked.
    private @Nullable String asked;
    private Consumer<Runnable> askedWhile = Runnable::run;

    AutoEstimate(
            final NamesPass namesPass,
            final GlossaryCalls calls,
            final SettingsViewModel settings,
            final MeasuredCalls measured) {
        this.namesPass = Objects.requireNonNull(namesPass, "namesPass");
        this.calls = Objects.requireNonNull(calls, "calls");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.measured = Objects.requireNonNull(measured, "measured");
        measured.storedChanges().addListener((observed, was, now) -> askAgain());
    }

    ReadOnlyObjectProperty<@Nullable NamesEstimate> property() {
        return estimate.getReadOnlyProperty();
    }

    /**
     * Asks the port for the estimate of a project and shows the answer.
     *
     * @param project the stored project the screen shows
     * @param current wraps what shows the answer so that it runs only while the opening that asked is current
     */
    void refresh(final String project, final Consumer<Runnable> current) {
        asked = project;
        askedWhile = current;
        final @Nullable ModelSelection chosen = settings.selection().orElse(null);
        log.debug("estimate of the automatic names pass asked for project {} on {}", project, chosen);
        calls.run(
                "names estimate",
                () -> namesPass.estimate(project, measured.stored(chosen)),
                answer -> current.accept(() -> {
                    estimate.set(answer.data());
                    log.debug("estimate of project {} is now {}", project, answer.data());
                }));
    }

    private void askAgain() {
        final String project = asked;
        log.debug("stored model speeds changed: the names pass estimate of project {} is asked again", project);
        if (project != null) {
            refresh(project, askedWhile);
        }
    }

    void clear() {
        estimate.set(null);
    }
}
