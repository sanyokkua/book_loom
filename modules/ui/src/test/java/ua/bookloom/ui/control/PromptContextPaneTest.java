package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.LiveCallFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.i18n.Messages;

/** The prompt-context section of a call: what Copy puts on the clipboard, and when the section shows at all. */
@SuppressWarnings("NullAway.Init")
class PromptContextPaneTest extends FxTestBase {

    private final Messages messages = new Messages(() -> Locale.ENGLISH);

    private PromptContextPane shown(final List<PromptSection> sections, final List<String> copied) {
        return ThemeTestSupport.onFx(() -> {
            final PromptContextPane pane = new PromptContextPane("prompt", messages, copied::add);
            pane.show(sections);
            new Scene(pane);
            pane.applyCss();
            return pane;
        });
    }

    // IF Copy dropped the order or the system/user split, THEN a pasted prompt could not be compared with the model's
    // behaviour.
    @Test
    void copy_putsEveryPartOnTheClipboardInTheOrderItWasSentUnderItsMessage() {
        final List<String> copied = new ArrayList<>();
        final PromptContextPane pane = shown(LiveCallFixtures.smallPrompt(), copied);

        ThemeTestSupport.onFx(() -> {
            ((Button) pane.lookup("#prompt-copy")).fire();
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
                Lovelace → Лавлейс

                [Characters]
                Nathaniel (he)

                [Previous pairs]
                Rain.
                Дощ.""");
    }

    private static final List<ChatMessage> SENT = List.of(
            new ChatMessage(ChatRole.SYSTEM, "Rules:\n1. Translate every clause.\n2. Keep the names."),
            new ChatMessage(ChatRole.USER, "Translate from English to Ukrainian.\n<s id=\"1\">He left.</s>"));

    private PromptContextPane shownWithSent(final List<PromptSection> sections, final List<String> copied) {
        return ThemeTestSupport.onFx(() -> {
            final PromptContextPane pane = new PromptContextPane("prompt", messages, copied::add);
            pane.show(sections, SENT);
            new Scene(pane);
            pane.applyCss();
            return pane;
        });
    }

    // IF the switch did not change the view, THEN the person could not see the numbered rules that were really sent.
    @Test
    void fullPromptSwitch_showsTheMessagesAsSentAndCopyPutsThemOnTheClipboard() {
        final List<String> copied = new ArrayList<>();
        final PromptContextPane pane = shownWithSent(LiveCallFixtures.smallPrompt(), copied);
        assertThat(pane.getText()).isEqualTo("Prompt context · 5 parts");

        ThemeTestSupport.onFx(() -> {
            ((CheckBox) pane.lookup("#prompt-full")).fire();
            ((Button) pane.lookup("#prompt-copy")).fire();
            return null;
        });

        assertThat(pane.getText()).isEqualTo("Full prompt · 2 messages");
        assertThat(copied).containsExactly("""
                == System message ==

                Rules:
                1. Translate every clause.
                2. Keep the names.

                == User message ==

                Translate from English to Ukrainian.
                <s id="1">He left.</s>""");
        ThemeTestSupport.onFx(() -> {
            pane.setExpanded(true);
            return null;
        });
        assertThat(((TextArea) pane.lookup("#prompt-body")).getText()).startsWith("== System message ==\n\nRules:");
    }

    // IF Copy read the text the open body holds, THEN a closed section would copy nothing.
    @Test
    void copy_sectionClosed_buildsTheTextOnDemand() {
        final List<String> copied = new ArrayList<>();
        final PromptContextPane pane = shown(
                List.of(new PromptSection("summary", "[Book so far]", PromptSection.Origin.USER, List.of("Rain."))),
                copied);

        ThemeTestSupport.onFx(() -> {
            ((Button) pane.lookup("#prompt-copy")).fire();
            return null;
        });

        assertThat(((TextArea) pane.lookup("#prompt-body")).getText()).isEmpty();
        assertThat(copied).containsExactly("== User message ==\n\n[Book so far]\nRain.");
    }

    @Test
    void fullPromptSwitch_switchedOffAgain_showsThePartsAgain() {
        final PromptContextPane pane = shownWithSent(LiveCallFixtures.smallPrompt(), new ArrayList<>());

        ThemeTestSupport.onFx(() -> {
            final CheckBox full = (CheckBox) pane.lookup("#prompt-full");
            full.fire();
            full.fire();
            return null;
        });

        assertThat(pane.getText()).isEqualTo("Prompt context · 5 parts");
    }

    // A call with no parts of its own has nothing to switch between: it shows the whole prompt and no switch.
    @Test
    void show_callWithoutPartsButWithMessages_showsTheFullPromptAndNoSwitch() {
        final PromptContextPane pane = shownWithSent(List.of(), new ArrayList<>());

        assertThat(pane.getText()).isEqualTo("Full prompt · 2 messages");
        assertThat(pane.lookup("#prompt-full").isVisible()).isFalse();
    }

    // IF a call that sent nothing left an empty box standing, THEN the person could open it.
    @Test
    void show_noPartsAndNoMessages_hidesTheSection() {
        final PromptContextPane pane = shownWithSent(List.of(), new ArrayList<>());
        ThemeTestSupport.onFx(() -> {
            pane.show(List.of(), List.of());
            return null;
        });

        assertThat(pane.isVisible()).isFalse();
    }

    // IF a part with no heading of its own showed nothing to name it, THEN its lines would float unlabelled.
    @Test
    void show_partWithoutHeading_isNamedBySlot() {
        final PromptContextPane pane = shown(
                List.of(new PromptSection("itemTokens", "", PromptSection.Origin.USER, List.of("1: ⟦g1⟧"))),
                new ArrayList<>());

        assertThat(pane.plainText()).isEqualTo("== User message ==\n\nitemTokens\n1: ⟦g1⟧");
    }

    // IF an empty prompt left a section standing, THEN the person could open an empty box.
    @Test
    void show_noSections_hidesTheSection() {
        final PromptContextPane pane = shown(List.of(), new ArrayList<>());

        assertThat(pane.isVisible()).isFalse();
        assertThat(pane.isManaged()).isFalse();
    }

    // IF the section and its Copy carried no hover explanation, THEN a person could not learn what they hold.
    @Test
    void sectionAndCopy_carryAHoverExplanation() {
        final PromptContextPane pane = shown(LiveCallFixtures.smallPrompt(), new ArrayList<>());

        assertThat(TooltipProbe.tipText(pane)).startsWith("Every filled part of the request");
        assertThat(TooltipProbe.tipText(pane.lookup("#prompt-copy"))).startsWith("Copies every part");
    }
}
