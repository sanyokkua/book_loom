package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.NamesStyleViewModel;

/**
 * The Scan · Review · Translate · Stop bar both cards of Names &amp; style carry, so the two lists are driven the same
 * way. Only one model action runs at a time across both cards, so a running action disables all six buttons and shows
 * Stop on both, whichever card started it.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ModelActionBar {

    /**
     * What a card's three actions do.
     *
     * @param scan finds more rows
     * @param review asks the model which rows to keep
     * @param translate asks the model for the missing targets or renderings
     */
    record Actions(Runnable scan, Runnable review, Runnable translate) {}

    /**
     * The words of one card's three actions; Stop reads the same on both cards.
     *
     * @param scan the caption of Scan
     * @param scanTip its hover explanation
     * @param review the caption of Review
     * @param reviewTip its hover explanation
     * @param translate the caption of Translate
     * @param translateTip its hover explanation
     */
    record Words(
            MessageKey scan,
            MessageKey scanTip,
            MessageKey review,
            MessageKey reviewTip,
            MessageKey translate,
            MessageKey translateTip) {}

    /**
     * The four buttons of one card's bar, to be laid out by the toolbar that carries them: it folds Review and
     * Translate into its menu when the card is narrow, which only works if each is a part of that toolbar.
     *
     * @param scan Scan
     * @param review Review
     * @param translate Translate
     * @param stop Stop, shown while a model action runs
     */
    record Bar(Button scan, Button review, Button translate, Button stop) {

        /** {@return the buttons in the order they read: Scan, Review, Translate, Stop} */
        List<Button> buttons() {
            return List.of(scan, review, translate, stop);
        }
    }

    /**
     * Builds the buttons of one card's bar.
     *
     * @param messages the catalogue the captions and explanations come from
     * @param card the card's id prefix, which the buttons' ids extend: {@code card-scan}, {@code card-review},
     *     {@code card-translate} and {@code card-stop}
     * @param words the captions and hover explanations of the three actions
     * @param actions what the three actions do
     * @param names the screen's state, whose stop ends whichever action runs
     * @return the four buttons
     */
    static Bar build(
            final Messages messages,
            final String card,
            final Words words,
            final Actions actions,
            final NamesStyleViewModel names) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(card, "card");
        final Button scan = button(messages, card + "-scan", words.scan(), words.scanTip(), actions.scan());
        final Button review = button(messages, card + "-review", words.review(), words.reviewTip(), actions.review());
        final Button translate =
                button(messages, card + "-translate", words.translate(), words.translateTip(), actions.translate());
        final Button stop =
                button(messages, card + "-stop", MessageKey.BAR_STOP, MessageKey.BAR_STOP_TIP, names::stopModel);
        explainWhenBlocked(messages, scan, words.scanTip(), names);
        explainWhenBlocked(messages, review, words.reviewTip(), names);
        explainWhenBlocked(messages, translate, words.translateTip(), names);
        stop.visibleProperty().bind(names.busy());
        stop.managedProperty().bind(names.busy());
        return new Bar(scan, review, translate, stop);
    }

    /**
     * Disables a model action while the screen's own work or other model work runs and, for the latter, makes its hover
     * say why ("Pause the translation first — BookLoom sends one model request at a time"). The visible line under the
     * table says it too, as a disabled button shows no tooltip on every platform.
     */
    static void explainWhenBlocked(
            final Messages messages, final Button model, final MessageKey tip, final NamesStyleViewModel names) {
        model.disableProperty().bind(names.busy().or(names.modelBlockedReason().isNotEmpty()));
        Tips.installLive(
                model,
                Bindings.createStringBinding(
                        () -> names.modelBlockedReason().get().isEmpty()
                                ? messages.get(tip)
                                : names.modelBlockedReason().get(),
                        names.modelBlockedReason()));
    }

    private static Button button(
            final Messages messages,
            final String id,
            final MessageKey caption,
            final MessageKey tip,
            final Runnable onPress) {
        final Button button = Tips.install(messages, new Button(messages.get(caption)), tip);
        button.setId(id);
        button.getStyleClass().add("btn-ghost");
        // A caption is never cut to an ellipsis: the toolbar folds or hides whole parts instead.
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setMaxHeight(Double.MAX_VALUE);
        button.setOnAction(event -> {
            log.debug("{} pressed", id);
            onPress.run();
        });
        return button;
    }
}
