package ua.bookloom.ui.screen;

import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.OpenedBook;
import ua.bookloom.ui.state.RunMode;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The card a Translating screen shows before any run exists: the book, the model, the review mode, the quality dial
 * and how many segments are still to translate, so that a person sees what Start will do before pressing it.
 *
 * <p>Every value is bound to what it reads, so a brief or a model changed on another screen shows here on return
 * without the card being rebuilt. The card logs nothing: its labels only follow their sources.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TranslatingReadyCard {

    static Node build(
            final TranslatingViewModel viewModel,
            final StateMirror mirror,
            final CurrentProject current,
            final Messages messages) {
        Objects.requireNonNull(viewModel, "viewModel");
        final NumberFormat grouping = NumberFormat.getIntegerInstance(messages.locale());
        final String review = messages.get(
                MessageKey.TRANSLATING_READY_REVIEW_MODE,
                viewModel.reviewMode().name().toLowerCase(Locale.ROOT));
        final Label book = TranslatingFigures.boundLabel(
                "translating-ready-book",
                "kv-value",
                () -> bookName(mirror.runFileName().get(), current.book().get()),
                mirror.runFileName(),
                current.book());
        final Label pending = TranslatingFigures.boundLabel(
                "translating-ready-pending",
                "kv-value",
                () -> grouping.format(viewModel.pendingCount().get()),
                viewModel.pendingCount());
        return BriefCards.card(
                "translating-ready-card",
                messages,
                MessageKey.TRANSLATING_READY_TITLE,
                row("book", MessageKey.TRANSLATING_READY_BOOK, book, messages),
                row("model", MessageKey.TRANSLATING_READY_MODEL, modelLabel(viewModel, messages), messages),
                row("review", MessageKey.TRANSLATING_READY_REVIEW, fixed("translating-ready-review", review), messages),
                row("dial", MessageKey.TRANSLATING_READY_DIAL, dialLabel(current, messages), messages),
                row("pending", MessageKey.TRANSLATING_READY_PENDING, pending, messages),
                modelHint(viewModel, messages));
    }

    // Before a first start: "not chosen" alone does not say where a model is chosen.
    private static Node modelHint(final TranslatingViewModel viewModel, final Messages messages) {
        final Label hint = BriefCards.hint(messages, MessageKey.TRANSLATING_READY_MODEL_HINT);
        hint.setId("translating-ready-model-hint");
        return BriefCards.shownWhile(
                hint,
                Bindings.createBooleanBinding(() -> viewModel.modelText().get().isBlank(), viewModel.modelText()));
    }

    private static Label modelLabel(final TranslatingViewModel viewModel, final Messages messages) {
        return TranslatingFigures.boundLabel(
                "translating-ready-model",
                "kv-value",
                () -> viewModel.modelText().get().isBlank()
                        ? messages.get(MessageKey.SETTINGS_MODEL_NOT_CHOSEN)
                        : viewModel.modelText().get(),
                viewModel.modelText());
    }

    private static Label dialLabel(final CurrentProject current, final Messages messages) {
        return TranslatingFigures.boundLabel(
                "translating-ready-dial",
                "kv-value",
                () -> current.brief().get() == null
                        ? ""
                        : messages.get(RunMode.dialKey(current.brief().get().dial())),
                current.brief());
    }

    private static String bookName(final @Nullable String runFile, final @Nullable OpenedBook book) {
        if (runFile != null) {
            return runFile;
        }
        if (book == null) {
            return "";
        }
        final Path name = book.source().getFileName();
        return name == null ? book.source().toString() : name.toString();
    }

    private static Label fixed(final String id, final String text) {
        final Label label = new Label(text);
        label.setId(id);
        label.getStyleClass().add("kv-value");
        return label;
    }

    private static Node row(final String name, final MessageKey key, final Label value, final Messages messages) {
        final Label caption = new Label(messages.get(key));
        caption.getStyleClass().add("kv-key");
        value.setWrapText(true);
        final HBox row = new HBox(caption, value);
        row.setId("translating-ready-row-" + name);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("kv");
        return row;
    }
}
