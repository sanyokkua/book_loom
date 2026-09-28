package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.inject.Guice;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.PipelineModule;

/** Verifies template loading, slot checking and optional-block rendering. */
class PromptTemplatesTest {

    private static final PromptTemplates.ResourceLoader BUNDLED =
            fileName -> PromptTemplates.class.getResourceAsStream(fileName);

    private static PromptTemplates.ResourceLoader bundledExcept(final String file, final String replacement) {
        return fileName -> fileName.equals(file)
                ? new ByteArrayInputStream(replacement.getBytes(StandardCharsets.UTF_8))
                : BUNDLED.open(fileName);
    }

    // A typo would otherwise send the model a literal {{sumary}}.
    @Test
    void load_undeclaredSlot_namesFileAndSlot() {
        final var loader = bundledExcept("draft.user.prompt", "{{sumary}} {{source}} {{target}} {{tokens}} {{text}}");

        assertThatThrownBy(() -> new PromptTemplates(loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("draft.user.prompt")
                .hasMessageContaining("sumary");
    }

    // A file that forgot a required slot would silently drop the segment text.
    @Test
    void load_requiredSlotMissing_namesFileAndSlot() {
        final var loader = bundledExcept("draft.user.prompt", "{{source}} {{target}} {{tokens}}");

        assertThatThrownBy(() -> new PromptTemplates(loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("draft.user.prompt")
                .hasMessageContaining("text");
    }

    @Test
    void load_missingFile_namesFile() {
        final PromptTemplates.ResourceLoader loader = fileName -> null;

        assertThatThrownBy(() -> new PromptTemplates(loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("draft.system.prompt");
    }

    @Test
    void renderUser_emptyOptionalSummary_removesWholeBlock() {
        final String user = new PromptTemplates()
                .renderUser(
                        PromptName.DRAFT,
                        Map.of(
                                "source",
                                "English (en)",
                                "target",
                                "Ukrainian (uk)",
                                "tokens",
                                "(none)",
                                "text",
                                "Hi.",
                                "summary",
                                ""));

        assertThat(user)
                .startsWith("Translate from English (en) to Ukrainian (uk).\n")
                .doesNotContain("Book so far");
    }

    @Test
    void renderUser_filledOptionalSummary_keepsBodyWithoutMarkers() {
        final String user = new PromptTemplates()
                .renderUser(
                        PromptName.DRAFT,
                        Map.of(
                                "source",
                                "A",
                                "target",
                                "B",
                                "tokens",
                                "(none)",
                                "text",
                                "Hi.",
                                "summary",
                                "It began."));

        assertThat(user)
                .startsWith("[Book so far — context only; do NOT re-translate it]\nIt began.\n\nTranslate from A to B.")
                .doesNotContain("{{");
    }

    // A slot value is book text and must be inserted literally, never re-read as a slot or a replacement group.
    @Test
    void renderUser_valueLooksLikeSlotOrReplacement_isInsertedLiterally() {
        final String user = new PromptTemplates()
                .renderUser(
                        PromptName.DRAFT,
                        Map.of("source", "A", "target", "B", "tokens", "(none)", "text", "{{source}} $1 \\"));

        assertThat(user).contains("<Text>\n{{source}} $1 \\\n</Text>");
    }

    @Test
    void renderUser_undeclaredValue_isRejected() {
        assertThatThrownBy(() -> new PromptTemplates().renderUser(PromptName.STRUCTURAL_REPAIR, Map.of("nope", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void renderSystem_callWithoutSystemTemplate_isRejected() {
        assertThatThrownBy(() -> new PromptTemplates().renderSystem(PromptName.STRUCTURAL_REPAIR, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void injector_pipelineModule_loadsEveryTemplate() {
        final var injector = Guice.createInjector(new PipelineModule(), new DocumentModule());

        assertThat(injector.getInstance(PromptTemplates.class)).isNotNull();
    }
}
