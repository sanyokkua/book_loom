package ua.bookloom.ui.screen;

import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.OpenedBook;
import ua.bookloom.ui.state.QuantChipModel;
import ua.bookloom.ui.state.RunMode;
import ua.bookloom.ui.state.RunStateView;
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

    private static final String KV_VALUE = "kv-value";
    private static final String FOLLOW_KEY = "bookloom.quant.follow";
    private static final double ROW_GAP = 8;

    static Node build(
            final TranslatingViewModel viewModel,
            final StateMirror mirror,
            final CurrentProject current,
            final ReadOnlyObjectProperty<RunStateView> runs,
            final Messages messages,
            final Label languageLine) {
        Objects.requireNonNull(viewModel, "viewModel");
        Objects.requireNonNull(runs, "runs");
        final NumberFormat grouping = NumberFormat.getIntegerInstance(messages.locale());
        final String review = messages.get(
                MessageKey.TRANSLATING_READY_REVIEW_MODE,
                viewModel.reviewMode().name().toLowerCase(Locale.ROOT));
        final Label book = boundLabel(
                "translating-ready-book",
                KV_VALUE,
                () -> bookName(mirror.runFileName().get(), current.book().get()),
                mirror.runFileName(),
                current.book());
        return BriefCards.card(
                "translating-ready-card",
                messages,
                MessageKey.TRANSLATING_READY_TITLE,
                row("book", MessageKey.TRANSLATING_READY_BOOK, book, messages),
                modelRow(viewModel, messages),
                languageLine,
                row("review", MessageKey.TRANSLATING_READY_REVIEW, fixed("translating-ready-review", review), messages),
                row("dial", MessageKey.TRANSLATING_READY_DIAL, dialLabel(current, messages), messages),
                row("pending", MessageKey.TRANSLATING_READY_PENDING, pendingLabel(viewModel, grouping), messages),
                modelHint(viewModel, messages),
                readyLine(runs, messages));
    }

    private static Label pendingLabel(final TranslatingViewModel viewModel, final NumberFormat grouping) {
        return boundLabel(
                "translating-ready-pending",
                KV_VALUE,
                () -> grouping.format(viewModel.pendingCount().get()),
                viewModel.pendingCount());
    }

    // The run-state row's own line ("Nothing translated yet.", where Continue translation picks up).
    private static Label readyLine(final ReadOnlyObjectProperty<RunStateView> runs, final Messages messages) {
        final Label line = boundLabel(
                "translating-ready-line", "muted", () -> runs.get().liveLine().in(messages), runs);
        line.setWrapText(true);
        return line;
    }

    // Before a first start: "not chosen" alone does not say where a model is chosen.
    private static Node modelHint(final TranslatingViewModel viewModel, final Messages messages) {
        final Label hint = BriefCards.hint(messages, MessageKey.TRANSLATING_READY_MODEL_HINT);
        hint.setId("translating-ready-model-hint");
        return Visibility.bind(
                hint,
                Bindings.createBooleanBinding(() -> viewModel.modelText().get().isBlank(), viewModel.modelText()));
    }

    private static Label modelLabel(final TranslatingViewModel viewModel, final Messages messages) {
        return boundLabel(
                "translating-ready-model",
                KV_VALUE,
                () -> viewModel.modelText().get().isBlank()
                        ? messages.get(MessageKey.SETTINGS_MODEL_NOT_CHOSEN)
                        : viewModel.modelText().get(),
                viewModel.modelText());
    }

    // The model's quant chip follows its value; with no quant known there is no chip node at all.
    private static Node modelRow(final TranslatingViewModel viewModel, final Messages messages) {
        final Label value = modelLabel(viewModel, messages);
        final Node row = row("model", MessageKey.TRANSLATING_READY_MODEL, value, messages);
        final HBox box = (HBox) row;
        HBox.setHgrow(value, Priority.ALWAYS);
        value.setMaxWidth(Double.MAX_VALUE);
        final Label chip = QuantChip.blank(messages);
        HBox.setMargin(chip, new Insets(0, 0, 0, ROW_GAP));
        final ChangeListener<QuantChipModel> follow = (observed, was, now) -> showChip(box, chip, now, messages);
        // The view model outlives the card, so the listener is weak and the row, which lives as long as the card
        // does, keeps it: the chip is not in the row while it has nothing to say, so it cannot be the keeper.
        box.getProperties().put(FOLLOW_KEY, follow);
        viewModel.quantChip().addListener(new WeakChangeListener<>(follow));
        showChip(box, chip, viewModel.quantChip().getValue(), messages);
        return row;
    }

    private static void showChip(
            final HBox box, final Label chip, final @Nullable QuantChipModel model, final Messages messages) {
        if (model == null) {
            box.getChildren().remove(chip);
            return;
        }
        QuantChip.show(chip, model, messages);
        if (!box.getChildren().contains(chip)) {
            box.getChildren().add(chip);
        }
    }

    private static Label dialLabel(final CurrentProject current, final Messages messages) {
        return boundLabel(
                "translating-ready-dial",
                KV_VALUE,
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

    private static Label boundLabel(
            final String id, final String styleClass, final Callable<String> text, final Observable... sources) {
        final Label label = new Label();
        label.setId(id);
        label.getStyleClass().add(styleClass);
        label.textProperty().bind(Bindings.createStringBinding(text, sources));
        return label;
    }

    private static Label fixed(final String id, final String text) {
        final Label label = new Label(text);
        label.setId(id);
        label.getStyleClass().add(KV_VALUE);
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
