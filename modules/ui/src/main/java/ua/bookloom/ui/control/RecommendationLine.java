package ua.bookloom.ui.control;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Label;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.MemorySource;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelRecommendation;

/**
 * The model advice lines every place that chooses a model shows. They are plain wrapping text: the pull command is
 * for the person to run, so there is no button and nothing here starts a process.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RecommendationLine {

    /**
     * The lines for this computer's memory.
     *
     * @param idPrefix the screen's id stem; the lines get {@code <stem>-recommend} and {@code <stem>-optin}
     * @return the labels in order, empty when the memory is unknown; never null
     */
    public static List<Node> nodes(final Messages messages, final MemorySource memory, final String idPrefix) {
        final List<Node> lines = new ArrayList<>();
        ModelRecommendation.of(memory.totalBytes()).ifPresent(rec -> {
            lines.add(line(
                    idPrefix + "-recommend",
                    messages.get(
                            MessageKey.MODEL_RECOMMEND_LINE,
                            Integer.toString(rec.gb()),
                            rec.recommended(),
                            ModelRecommendation.pullCommand(rec.recommended()))));
            rec.optIn()
                    .ifPresent(tag -> lines.add(line(
                            idPrefix + "-optin",
                            messages.get(
                                    MessageKey.MODEL_RECOMMEND_OPTIN, tag, ModelRecommendation.pullCommand(tag)))));
        });
        return lines;
    }

    private static Label line(final String id, final String text) {
        final Label label = Wrapped.label(text, "hint");
        label.setId(id);
        return label;
    }
}
