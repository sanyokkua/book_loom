package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.NamesEstimate;
import ua.bookloom.api.pipeline.NamesPass;

/**
 * What the automatic pass would cost for the open project's dial, asked of the port again whenever the screen opens or
 * the dial changes. A refused question leaves the estimate empty rather than the last one, so the button never promises
 * minutes nobody computed. FX thread only.
 */
@Slf4j
final class AutoEstimate {

    private final NamesPass namesPass;
    private final GlossaryCalls calls;
    private final ReadOnlyObjectWrapper<@Nullable NamesEstimate> estimate = new ReadOnlyObjectWrapper<>();

    AutoEstimate(final NamesPass namesPass, final GlossaryCalls calls) {
        this.namesPass = Objects.requireNonNull(namesPass, "namesPass");
        this.calls = Objects.requireNonNull(calls, "calls");
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
        log.debug("estimate of the automatic names pass asked for project {}", project);
        calls.run(
                "names estimate",
                () -> namesPass.estimate(project),
                answer -> current.accept(() -> {
                    estimate.set(answer.data());
                    log.debug("estimate of project {} is now {}", project, answer.data());
                }));
    }

    void clear() {
        estimate.set(null);
    }
}
