package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
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
