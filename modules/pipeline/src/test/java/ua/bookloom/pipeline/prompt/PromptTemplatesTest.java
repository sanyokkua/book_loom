package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.inject.Guice;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
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

    // The revision call holds one <Text> block with the masked target; the masked source sits outside it.
    @Test
    void renderUser_revisionWithoutFacts_holdsSourceAndOneTextBlock() {
        final String user = new PromptTemplates()
                .renderUser(
                        PromptName.REVISION,
                        Map.of(
                                "source",
                                "Sam opened the ⟦g0⟧old⟦g1⟧ door.",
                                "text",
                                "Сем відчинив ⟦g0⟧старі⟦g1⟧ двері."));

        assertThat(user)
                .isEqualTo("[Source]\nSam opened the ⟦g0⟧old⟦g1⟧ door.\n\n<Text>\nСем відчинив ⟦g0⟧старі⟦g1⟧ двері.\n"
                        + "</Text>\n\nReturn exactly one JSON object matching this schema: "
                        + "{\"target\":\"<revised translation>\"}\n");
    }

    @Test
    void renderUser_revisionWithFacts_namesThemBeforeTheSource() {
        final String user = new PromptTemplates()
                .renderUser(
                        PromptName.REVISION,
                        Map.of("source", "Sam left.", "text", "Сем пішов.", "resolvedFacts", "- Sam (Сем): female"));

        assertThat(user)
                .startsWith(
                        "[Resolved facts revealed later in the book]\n- Sam (Сем): female\n\n[Source]\nSam left.\n");
    }

    @Test
    void renderSystem_revision_fillsLanguagesAndStyle() {
        final String system = new PromptTemplates()
                .renderSystem(
                        PromptName.REVISION,
                        Map.of(
                                "sourceLanguage",
                                "English (en)",
                                "targetLanguage",
                                "Ukrainian (uk)",
                                "styleSheet",
                                "Neutral register.",
                                "foreignPassageRule",
                                "Keep foreign passages."));

        assertThat(system)
                .startsWith("You are performing a consistency revision on an already-translated book "
                        + "(English (en) → Ukrainian (uk)).")
                .contains("Style guidance:\nNeutral register.\n", "- Keep foreign passages.\n")
                .doesNotContain("{{");
    }

    @Test
    void injector_pipelineModule_loadsEveryTemplate() {
        final var injector = Guice.createInjector(new PipelineModule(), new DocumentModule(), new PersistenceModule());

        assertThat(injector.getInstance(PromptTemplates.class)).isNotNull();
    }
}
