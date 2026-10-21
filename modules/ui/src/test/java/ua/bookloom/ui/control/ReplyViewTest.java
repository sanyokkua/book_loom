package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.CallItem;
import ua.bookloom.api.pipeline.CallItemStatus;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.i18n.Messages;

/** The parsed reply of a call: a row per item with what became of it, and the raw reply behind Copy. */
@SuppressWarnings("NullAway.Init")
class ReplyViewTest extends FxTestBase {

    private static final String RAW = "{\"items\":[{\"id\":\"1\",\"target\":\"Він пішов.\"}]}";
    private static final Map<String, String> LOCATORS =
            Map.of("s-1", "ch1 · p01", "s-2", "ch1 · p02", "s-3", "ch1 · p03", "s-4", "ch1 · p04");

    private final Messages messages = new Messages(() -> Locale.ENGLISH);

    private static List<CallItem> fourItemsOneMissing() {
        return List.of(
                new CallItem("1", "s-1", "Він пішов.", CallItemStatus.OK, List.of()),
                new CallItem("2", "s-2", "Вона лишилась.", CallItemStatus.OK, List.of()),
                new CallItem("3", "s-3", null, CallItemStatus.MISSING, List.of()),
                new CallItem("4", "s-4", "Дощ.", CallItemStatus.OK, List.of()));
    }

    private ReplyView shown(
            final String reply,
            final List<CallItem> items,
            final Map<String, String> locators,
            final List<String> copied) {
        return ThemeTestSupport.onFx(() -> {
            final ReplyView view = new ReplyView("reply", messages, copied::add);
            view.show(reply, items, locators);
            view.setExpanded(true);
            new Scene(view);
            view.applyCss();
            view.layout();
            return view;
        });
    }

    private static List<String> texts(final Node root, final String selector) {
        return ThemeTestSupport.onFx(() -> root.lookupAll(selector).stream()
                .map(node -> ((Labeled) node).getText())
                .toList());
    }

    // IF a batch's missing id were hidden in a wall of JSON, THEN the person could not see which segment was lost.
    @Test
    void show_fourItemsOneMissing_hasFourRowsAndTheWordMissing() {
        final ReplyView view = shown(RAW, fourItemsOneMissing(), LOCATORS, new ArrayList<>());

        assertThat(texts(view, ".reply-result")).containsExactly("ok", "ok", "missing", "ok");
        assertThat(texts(view, ".parsed-head")).containsExactly("Id", "Segment", "Translation returned", "Result");
        assertThat(texts(view, ".parsed-cell")).hasSize(16);
        assertThat(texts(view, ".parsed-cell")).contains("ch1 · p03", "—");
    }

    // A refused item says why in the result cell, so the person need not open the log.
    @Test
    void show_itemWithProblems_namesThemInTheResult() {
        final List<CallItem> items =
                List.of(new CallItem("2", "s-2", "Дощ.", CallItemStatus.DUPLICATE, List.of("came back twice")));

        final ReplyView view = shown(RAW, items, LOCATORS, new ArrayList<>());

        assertThat(texts(view, ".reply-result")).containsExactly("duplicate · came back twice");
    }

    @Test
    void show_itemsPresent_titleCountsTheItemsAndRawBlockStartsCollapsed() {
        final ReplyView view = shown(RAW, fourItemsOneMissing(), LOCATORS, new ArrayList<>());

        assertThat(view.getText()).isEqualTo("Model reply · 4 items");
        assertThat(((TitledPane) view.lookup("#reply-raw")).isExpanded()).isFalse();
        assertThat(((TextArea) view.lookup("#reply-raw-body")).getText()).isEmpty();
    }

    // A reply with no parsed items (a reviewer, a scan) has nothing to tabulate: its text is open at once.
    @Test
    void show_replyWithoutItems_showsTheRawTextOpenAndNoGrid() {
        final ReplyView view = shown(RAW, List.of(), Map.of(), new ArrayList<>());

        assertThat(view.getText()).isEqualTo("Model reply");
        assertThat(texts(view, ".parsed-head")).isEmpty();
        assertThat(((TextArea) view.lookup("#reply-raw-body")).getText()).isEqualTo(RAW);
    }

    // IF Copy put the table on the clipboard, THEN the reply could not be compared with what the model said.
    @Test
    void copy_replyShown_putsTheReplyAsReceivedOnTheClipboard() {
        final List<String> copied = new ArrayList<>();
        final ReplyView view = shown(RAW, fourItemsOneMissing(), LOCATORS, copied);

        ThemeTestSupport.onFx(() -> {
            ((Button) view.lookup("#reply-copy")).fire();
            ((Button) view.lookup("#reply-raw-copy")).fire();
            return null;
        });

        assertThat(copied).containsExactly(RAW, RAW);
    }

    // IF a call not yet answered left a box standing, THEN the person could open an empty reply.
    @Test
    void show_noReply_hidesTheView() {
        final ReplyView view = ThemeTestSupport.onFx(() -> {
            final ReplyView fresh = new ReplyView("reply", messages, new ArrayList<String>()::add);
            fresh.show(null, List.of(), Map.of());
            return fresh;
        });

        assertThat(view.isVisible()).isFalse();
        assertThat(view.isManaged()).isFalse();
    }

    @Test
    void viewAndCopy_carryAHoverExplanation() {
        final ReplyView view = shown(RAW, fourItemsOneMissing(), LOCATORS, new ArrayList<>());

        assertThat(TooltipProbe.tipText(view)).startsWith("The model's answer");
        assertThat(TooltipProbe.tipText(view.lookup("#reply-copy"))).startsWith("Copies the model's reply");
    }
}
