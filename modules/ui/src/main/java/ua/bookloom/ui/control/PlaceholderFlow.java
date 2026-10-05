package ua.bookloom.ui.control;

import java.util.List;
import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import ua.bookloom.ui.state.ReadableText;

/**
 * A read-only rendering of a masked text in which each {@code ⟦gN⟧} formatting token is a small chip and the words a
 * finding quotes are marked, so a person reads the book's words without the raw tokens in the way.
 *
 * <p>It draws what {@link ReadableText} cuts and holds no state beyond the nodes of the text shown. Every colour comes
 * from the style classes {@code flow-text}, {@code placeholder-chip} and {@code evidence-mark}, which the theme defines
 * from its tokens.
 */
public final class PlaceholderFlow extends TextFlow {

    /**
     * Creates an empty flow.
     *
     * @param id the node id, so a test and a stylesheet can find it
     */
    public PlaceholderFlow(final String id) {
        setId(id);
        getStyleClass().add("placeholder-flow");
    }

    /**
     * Shows a text.
     *
     * @param text the masked text; never null
     * @param quotes the words to mark; never null
     */
    public void show(final String text, final List<String> quotes) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(quotes, "quotes");
        getChildren()
                .setAll(ReadableText.cut(text, quotes).stream()
                        .map(PlaceholderFlow::node)
                        .toList());
    }

    private static javafx.scene.Node node(final ReadableText.Piece piece) {
        return switch (piece.kind()) {
            case PLAIN -> plain(piece.text());
            case TOKEN -> label(piece.text(), "placeholder-chip");
            case EVIDENCE -> label(piece.text(), "evidence-mark");
        };
    }

    private static Text plain(final String words) {
        final Text text = new Text(words);
        text.getStyleClass().add("flow-text");
        return text;
    }

    private static Label label(final String words, final String styleClass) {
        final Label label = new Label(words);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
