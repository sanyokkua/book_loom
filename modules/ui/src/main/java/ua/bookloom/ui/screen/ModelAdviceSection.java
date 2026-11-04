package ua.bookloom.ui.screen;

import java.util.Objects;
import java.util.Optional;
import javafx.beans.binding.ObjectBinding;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.AdviceFollow;
import ua.bookloom.ui.state.AdviceReader;
import ua.bookloom.ui.state.AdviceReading;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ModelAdviceLines;
import ua.bookloom.ui.state.ModelEntry;
import ua.bookloom.ui.state.ModelForm;

/**
 * What the Models tab says about the chosen model before its settings: how the name was read, what its quant means,
 * what its maker declares for the open project's languages, the licence note and the thinking hint; and, at the tab's
 * foot, the date of the shipped model knowledge. Advice only: no line changes a setting or blocks the tab.
 */
final class ModelAdviceSection {

    private final VBox top;
    private final Label foot;
    // The reading is held weakly by what it observes; this field keeps it as long as the section lives.
    private final ObjectBinding<@Nullable AdviceReading> reading;

    ModelAdviceSection(
            final AdviceReader reader,
            final CurrentProject current,
            final ModelForm form,
            final Messages messages,
            final LanguageNames names) {
        Objects.requireNonNull(form, "form");
        final ModelAdviceLines lines = new ModelAdviceLines(messages, names);
        this.reading = AdviceFollow.of(
                reader, current, () -> selectionOf(form.selected().get()), form.selected());
        this.top = new VBox(
                4,
                AdviceLabels.plain("models-identity-line", reading, lines::identity),
                AdviceLabels.plain("models-quant-line", reading, said -> lines.quant(said, false)),
                AdviceLabels.plain("models-languages-line", reading, said -> Optional.of(lines.languages(said))),
                AdviceLabels.plain("models-licence-line", reading, lines::licence),
                AdviceLabels.plain("models-thinking-hint", reading, lines::thinking));
        top.setId("models-advice");
        this.foot = AdviceLabels.plain("models-facts-date", reading, lines::factsDate);
    }

    VBox node() {
        return top;
    }

    Label foot() {
        return foot;
    }

    private static Optional<ModelSelection> selectionOf(final @Nullable ModelEntry entry) {
        return entry == null ? Optional.empty() : Optional.of(new ModelSelection(entry.providerId(), entry.modelId()));
    }
}
