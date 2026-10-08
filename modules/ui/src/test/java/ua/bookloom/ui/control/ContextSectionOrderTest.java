package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.SnapshotRendering;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.SnapshotTmHit;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.i18n.Messages;

/** The review panel's context section lists its parts in the order the draft prompt sends them. */
@SuppressWarnings("NullAway.Init")
class ContextSectionOrderTest extends FxTestBase {

    private static final ContextSnapshot EVERYTHING = new ContextSnapshot(
            List.of("Дощ лив стіною."),
            List.of(
                    new SnapshotTerm("Lovelace", "Лавлейс", TermType.CHARACTER, Gender.MALE, true),
                    new SnapshotTerm("Bartimaeus", "Бартімеус", TermType.CHARACTER, Gender.MALE, false),
                    new SnapshotTerm("Nathaniel", "Натаніель", TermType.CHARACTER, Gender.MALE, false, true)),
            List.of(new SnapshotTmHit(SnapshotTmHit.TmHitKind.EXACT, "The amulet glowed.", "Амулет світився.")),
            "A djinni is summoned.",
            "Literary, warm.",
            List.of(new SnapshotRendering("magician", "чарівник")),
            List.of("Nathaniel (he)"));

    private final Messages messages = new Messages(() -> Locale.ENGLISH);

    private ContextSection shown(final ContextSnapshot context, final List<String> copied) {
        return ThemeTestSupport.onFx(() -> {
            final ContextSection section = new ContextSection("ctx", messages, copied::add);
            section.show(context);
            new Scene(section);
            section.applyCss();
            return section;
        });
    }

    private static List<String> headings(final Node root) {
        final List<String> found = new ArrayList<>();
        collect(root, found);
        return found;
    }

    private static void collect(final Node node, final List<String> found) {
        if (node instanceof Label label && label.getStyleClass().contains("context-heading")) {
            found.add(label.getText());
        }
        if (node instanceof Pane pane) {
            pane.getChildren().forEach(child -> collect(child, found));
        }
        if (node instanceof ScrollPane scroll && scroll.getContent() != null) {
            collect(scroll.getContent(), found);
        }
        if (node instanceof TitledPane titled && titled.getContent() != null) {
            collect(titled.getContent(), found);
        }
    }

    // IF the review desk listed the parts in another order than the prompt, THEN the person would misread what the
    // model saw first and last.
    @Test
    void show_everyPart_isListedInPromptOrder() {
        final ContextSection section = shown(EVERYTHING, new ArrayList<>());

        assertThat(ThemeTestSupport.onFx(() -> headings(section)))
                .containsExactly(
                        "Style sheet",
                        "Running summary",
                        "Names from the glossary",
                        "Locked names",
                        "Suggested renderings",
                        "Established renderings of recurring terms",
                        "Translation memory",
                        "Characters",
                        "Preceding translations");
    }

    // IF Copy put the parts in another order than the screen, THEN pasted context would not match what was read.
    @Test
    void copy_everyPart_putsThePartsOnTheClipboardInPromptOrder() {
        final List<String> copied = new ArrayList<>();
        final ContextSection section = shown(EVERYTHING, copied);

        ThemeTestSupport.onFx(() -> {
            ((Button) section.lookup("#ctx-copy")).fire();
            return null;
        });

        assertThat(copied).hasSize(1);
        assertThat(copied.get(0)
                        .lines()
                        .filter(line -> !line.startsWith(" ") && !line.isBlank())
                        .toList())
                .containsExactly(
                        "Style sheet",
                        "Running summary",
                        "Names from the glossary",
                        "Locked names",
                        "Suggested renderings",
                        "Established renderings of recurring terms",
                        "Translation memory",
                        "Characters",
                        "Preceding translations");
        assertThat(copied.get(0))
                .contains("magician → чарівник")
                .contains("Nathaniel (he)")
                .contains("Literary, warm.");
    }

    // IF the style sheet alone counted as context, THEN a draft sent nothing would not say so.
    @Test
    void show_onlyAStyleSheet_saysNothingWasSentBesides() {
        final ContextSection section =
                shown(new ContextSnapshot(List.of(), List.of(), List.of(), null, "Formal"), new ArrayList<>());

        assertThat(ThemeTestSupport.onFx(() -> headings(section))).isEmpty();
        assertThat(section.plainText()).isEqualTo("Nothing besides the segment and the style sheet.");
    }
}
