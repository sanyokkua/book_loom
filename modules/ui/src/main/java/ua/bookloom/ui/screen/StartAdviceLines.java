package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelAdviceLines;
import ua.bookloom.ui.state.StartAdvice;

/**
 * The advice shown under the Book brief's estimate before Start: the language line, the quant line, the licence note
 * and the thinking hint of the model the run will use. Each is plain text that holds its place only while it has
 * something to say, and none of them is a question: Start stays available.
 */
final class StartAdviceLines {

    private final VBox node;

    StartAdviceLines(final StartAdvice advice, final Messages messages, final LanguageNames names) {
        Objects.requireNonNull(advice, "advice");
        final ModelAdviceLines lines = new ModelAdviceLines(messages, names);
        this.node = new VBox(
                4,
                AdviceLabels.toned("start-language-line", advice.reading(), lines::languageLine),
                AdviceLabels.plain("start-quant-line", advice.reading(), said -> lines.quant(said, true)),
                AdviceLabels.plain("start-licence-line", advice.reading(), lines::licence),
                AdviceLabels.plain("start-thinking-line", advice.reading(), lines::thinking));
        node.setId("start-advice");
    }

    VBox node() {
        return node;
    }

    /** The ready card's language line: the same words as the Start one, under the model row. */
    static Label readyLanguageLine(final StartAdvice advice, final Messages messages, final LanguageNames names) {
        final ModelAdviceLines lines = new ModelAdviceLines(messages, names);
        return AdviceLabels.toned("translating-ready-language-line", advice.reading(), lines::languageLine);
    }
}
