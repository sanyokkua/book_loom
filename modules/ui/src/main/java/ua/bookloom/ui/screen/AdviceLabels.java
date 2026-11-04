package ua.bookloom.ui.screen;

import java.util.Optional;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Label;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.state.AdviceReading;
import ua.bookloom.ui.state.ModelAdviceLines;
import ua.bookloom.ui.state.Tone;

/**
 * Builds the wrapping lines of the advice: each follows a reading, holds no text while the reading has none, and leaves
 * the layout then. The label keeps its binding, so the line lives exactly as long as the node does.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AdviceLabels {

    private static final String HINT = "hint";

    /** A plain muted line worded by {@code words}. */
    static Label plain(
            final String id,
            final ObservableValue<@Nullable AdviceReading> reading,
            final Function<AdviceReading, Optional<String>> words) {
        final Label label = base(id);
        label.textProperty().bind(text(reading, words));
        return Visibility.bind(label, Bindings.isNotEmpty(label.textProperty()));
    }

    /** A line drawn in the tone its words carry. */
    static Label toned(
            final String id,
            final ObservableValue<@Nullable AdviceReading> reading,
            final Function<AdviceReading, Optional<ModelAdviceLines.Line>> words) {
        final Label label = base(id);
        final ObjectBinding<ModelAdviceLines.@Nullable Line> line = Bindings.createObjectBinding(
                () -> {
                    final AdviceReading current = reading.getValue();
                    return current == null ? null : words.apply(current).orElse(null);
                },
                reading);
        label.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> line.get() == null ? "" : line.get().text(), line));
        line.addListener((observed, was, now) -> tone(label, now));
        tone(label, line.get());
        return Visibility.bind(label, Bindings.isNotEmpty(label.textProperty()));
    }

    private static void tone(final Label label, final ModelAdviceLines.@Nullable Line line) {
        label.getStyleClass().removeAll(Tone.statusClasses());
        if (line != null) {
            label.getStyleClass().add(line.tone().statusClass());
        }
    }

    private static StringBinding text(
            final ObservableValue<@Nullable AdviceReading> reading,
            final Function<AdviceReading, Optional<String>> words) {
        return Bindings.createStringBinding(
                () -> {
                    final AdviceReading current = reading.getValue();
                    return current == null ? "" : words.apply(current).orElse("");
                },
                reading);
    }

    private static Label base(final String id) {
        final Label label = Wrapped.wholeLines(new Label());
        label.setId(id);
        label.getStyleClass().add(HINT);
        return label;
    }
}
