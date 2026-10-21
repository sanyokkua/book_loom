package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.LiveCallFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.i18n.Messages;

/** The parsed prompt of a call: each slot in its own shape, what Copy puts on the clipboard, and when it shows. */
@SuppressWarnings("NullAway.Init")
class PromptViewTest extends FxTestBase {

    private static final List<ChatMessage> SENT = List.of(
            new ChatMessage(ChatRole.SYSTEM, "Rules:\n1. Translate every clause.\n2. Keep the names."),
            new ChatMessage(ChatRole.USER, "Translate from English to Ukrainian.\n<s id=\"1\">He left.</s>"));

    private final Messages messages = new Messages(() -> Locale.ENGLISH);

    private PromptView shown(
            final List<PromptSection> sections,
            final List<ChatMessage> sent,
            final Map<String, String> locators,
            final List<String> copied) {
        return ThemeTestSupport.onFx(() -> {
            final PromptView view = new PromptView("prompt", messages, copied::add);
            view.show(sections, sent, locators);
            view.setExpanded(true);
            new Scene(view);
            view.applyCss();
            view.layout();
            return view;
        });
    }

    private PromptView shown(final List<PromptSection> sections, final List<String> copied) {
        return shown(sections, List.of(), Map.of(), copied);
    }

    private static List<String> texts(final Node root, final String selector) {
        return ThemeTestSupport.onFx(() -> root.lookupAll(selector).stream()
                .map(node -> ((Labeled) node).getText())
                .toList());
    }

    private static List<String> sectionTitles(final Node root) {
        return ThemeTestSupport.onFx(() -> root.lookupAll(".titled-pane").stream()
                .filter(TitledPane.class::isInstance)
                .map(node -> ((TitledPane) node).getText())
                .filter(title -> title.startsWith("["))
                .toList());
    }

    static Stream<Arguments> slots() {
        return Stream.concat(termSlots(), otherSlots());
    }

    private static Stream<Arguments> otherSlots() {
        return Stream.of(
                Arguments.of(
                        "items",
                        List.of("<s id=\"1\">He left.</s>", "<s id=\"2\">She stayed.</s>"),
                        Map.of("1", "ch1 · p01", "2", "ch1 · p02"),
                        ".parsed-head",
                        List.of("Id", "Segment", "Text")),
                Arguments.of(
                        "items",
                        List.of("<s id=\"1\">He left.</s>", "<s id=\"2\">She stayed.</s>"),
                        Map.of("1", "ch1 · p01", "2", "ch1 · p02"),
                        ".parsed-cell",
                        List.of("1", "ch1 · p01", "He left.", "2", "ch1 · p02", "She stayed.")),
                Arguments.of(
                        "precedingPairs",
                        List.of("Source: Rain.", "Translation: Дощ."),
                        Map.of(),
                        ".parsed-cell",
                        List.of("Rain.", "Дощ.")),
                Arguments.of(
                        "characters",
                        List.of("Edwin (he)", "Mara (she)"),
                        Map.of(),
                        ".prompt-plain",
                        List.of("Edwin (he)\nMara (she)")));
    }

    private static Stream<Arguments> termSlots() {
        return Stream.of(
                Arguments.of(
                        "styleSheet",
                        List.of("Literary, warm.", "Plain words."),
                        Map.of(),
                        ".prompt-bullet",
                        List.of("• Literary, warm.", "• Plain words.")),
                Arguments.of(
                        "glossaryTerms",
                        List.of("Ashdown → Ешдаун (place)", "Edwin → Едвін"),
                        Map.of(),
                        ".parsed-head",
                        List.of("Term", "Rendering", "Note")),
                Arguments.of(
                        "glossaryTerms",
                        List.of("Ashdown → Ешдаун (place)", "Edwin → Едвін"),
                        Map.of(),
                        ".parsed-cell",
                        List.of("Ashdown", "Ешдаун", "place", "Edwin", "Едвін", "")));
    }

    // IF a slot were not parsed, THEN the person would read a wall of lines instead of the shape the model was given.
    @ParameterizedTest
    @MethodSource("slots")
    void show_slot_rendersItsOwnShape(
            final String slot,
            final List<String> lines,
            final Map<String, String> locators,
            final String selector,
            final List<String> expected) {
        final PromptView view = shown(
                List.of(new PromptSection(slot, "[Heading]", PromptSection.Origin.USER, lines)),
                List.of(),
                locators,
                new ArrayList<>());

        assertThat(texts(view, selector)).containsExactlyElementsOf(expected);
    }

    static Stream<Arguments> grids() {
        return Stream.of(
                Arguments.of("glossaryTerms", List.of("Ashdown → Ешдаун (place)"), 3),
                Arguments.of("items", List.of("<s id=\"2\">He left.</s>"), 3),
                Arguments.of("precedingPairs", List.of("Source: Rain.", "Translation: Дощ."), 2));
    }

    // IF a grid slot lost a column, THEN a rendering or a note would be glued to the term.
    @ParameterizedTest
    @MethodSource("grids")
    void show_gridSlot_hasItsColumns(final String slot, final List<String> lines, final int columns) {
        final PromptView view = shown(
                List.of(new PromptSection(slot, "[Heading]", PromptSection.Origin.USER, lines)), new ArrayList<>());

        final int widest = ThemeTestSupport.onFx(() -> view.lookupAll(".parsed-head").stream()
                        .mapToInt(GridPane::getColumnIndex)
                        .max()
                        .orElse(-1)
                + 1);

        assertThat(widest).isEqualTo(columns);
    }

    // The batch id the item carries is shown as it is when no segment is known for it.
    @Test
    void show_itemWithoutKnownSegment_showsADashForTheSegment() {
        final PromptView view = shown(
                List.of(new PromptSection("items", "", PromptSection.Origin.USER, List.of("<s id=\"2\">He left.</s>"))),
                new ArrayList<>());

        assertThat(texts(view, ".parsed-cell")).containsExactly("2", "—", "He left.");
    }

    // IF the system and user parts shared one list, THEN the person could not tell the rules from the text worked on.
    @Test
    void show_systemAndUserParts_areTwoRoleCardsInSentOrder() {
        final PromptView view = shown(LiveCallFixtures.smallPrompt(), new ArrayList<>());

        assertThat(texts(view, ".prompt-role")).containsExactly("System message", "User message");
        assertThat(sectionTitles(view))
                .containsExactly("[Book so far]", "[Glossary]", "[Characters]", "[Previous pairs]");
    }

    // IF Copy dropped the order or the system/user split, THEN a pasted prompt could not be compared with the model's
    // behaviour.
    @Test
    void copy_putsEveryPartOnTheClipboardInTheOrderItWasSentUnderItsMessage() {
        final List<String> copied = new ArrayList<>();
        final PromptView view = shown(LiveCallFixtures.smallPrompt(), copied);

        ThemeTestSupport.onFx(() -> {
            ((Button) view.lookup("#prompt-copy")).fire();
            return null;
        });

        assertThat(copied).containsExactly("""
                == System message ==

                Style:
                Literary, warm.

                == User message ==

                [Book so far]
                A djinni is summoned.

                [Glossary]
                Ashdown → Ешдаун

                [Characters]
                Edwin (he)

                [Previous pairs]
                Rain.
                Дощ.""");
    }

    // IF the raw block did not put the messages as sent on the clipboard, THEN the numbered rules that were really sent
    // could not be compared.
    @Test
    void rawBlockCopy_messagesExist_putsTheMessagesAsSentOnTheClipboard() {
        final List<String> copied = new ArrayList<>();
        final PromptView view = shown(LiveCallFixtures.smallPrompt(), SENT, Map.of(), copied);
        assertThat(view.getText()).isEqualTo("Prompt context · 5 parts");

        ThemeTestSupport.onFx(() -> {
            ((Button) view.lookup("#prompt-sent-copy")).fire();
            return null;
        });

        assertThat(copied).containsExactly("""
                == System message ==

                Rules:
                1. Translate every clause.
                2. Keep the names.

                == User message ==

                Translate from English to Ukrainian.
                <s id="1">He left.</s>""");
    }

    @Test
    void rawBlock_partsPresent_startsCollapsedAndFillsOnlyWhenOpened() {
        final PromptView view = shown(LiveCallFixtures.smallPrompt(), SENT, Map.of(), new ArrayList<>());
        final TitledPane raw = (TitledPane) view.lookup("#prompt-sent");
        final TextArea body = (TextArea) view.lookup("#prompt-sent-body");
        assertThat(raw.isExpanded()).isFalse();
        assertThat(body.getText()).isEmpty();

        ThemeTestSupport.onFx(() -> {
            raw.setExpanded(true);
            return null;
        });

        assertThat(body.getText()).startsWith("== System message ==\n\nRules:");
    }

    // A call with no parts of its own has nothing to parse: the messages as sent are open at once.
    @Test
    void show_callWithoutPartsButWithMessages_showsTheMessagesOpen() {
        final PromptView view = shown(List.of(), SENT, Map.of(), new ArrayList<>());

        assertThat(view.getText()).isEqualTo("Full prompt · 2 messages");
        assertThat(((TitledPane) view.lookup("#prompt-sent")).isExpanded()).isTrue();
    }

    // IF a call that sent nothing left an empty box standing, THEN the person could open it.
    @Test
    void show_noPartsAndNoMessages_hidesTheView() {
        final PromptView view = shown(List.of(), new ArrayList<>());

        assertThat(view.isVisible()).isFalse();
        assertThat(view.isManaged()).isFalse();
    }

    // IF a part with no heading of its own showed nothing to name it, THEN its lines would float unlabelled.
    @Test
    void show_partWithoutHeading_isNamedBySlot() {
        final PromptView view = shown(
                List.of(new PromptSection("itemTokens", "", PromptSection.Origin.USER, List.of("1: ⟦g1⟧"))),
                new ArrayList<>());

        assertThat(view.plainText()).isEqualTo("== User message ==\n\nitemTokens\n1: ⟦g1⟧");
        assertThat(texts(view, ".prompt-plain")).containsExactly("1: ⟦g1⟧");
    }

    // IF the view and its Copy carried no hover explanation, THEN a person could not learn what they hold.
    @Test
    void viewAndCopy_carryAHoverExplanation() {
        final PromptView view = shown(LiveCallFixtures.smallPrompt(), new ArrayList<>());

        assertThat(TooltipProbe.tipText(view)).startsWith("Every filled part of the request");
        assertThat(TooltipProbe.tipText(view.lookup("#prompt-copy"))).startsWith("Copies every part");
    }
}
