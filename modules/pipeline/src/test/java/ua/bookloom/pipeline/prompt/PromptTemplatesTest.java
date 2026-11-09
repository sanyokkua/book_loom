package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.inject.Guice;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.CallLedgerTestModule;
import ua.bookloom.pipeline.PipelineModule;
import ua.bookloom.pipeline.ReviewModeTestModule;

/** Verifies template loading, slot checking and optional-block rendering. */
class PromptTemplatesTest {

    private static final PromptTemplates.ResourceLoader BUNDLED = PromptTemplates.class::getResourceAsStream;

    private static PromptTemplates.ResourceLoader bundledExcept(final String file, final String replacement) {
        return fileName -> fileName.equals(file)
                ? new ByteArrayInputStream(replacement.getBytes(StandardCharsets.UTF_8))
                : BUNDLED.open(fileName);
    }

    // A typo would otherwise send the model a literal {{sumary}}.
    @Test
    void load_undeclaredSlot_namesFileAndSlot() {
        final var loader = bundledExcept("directed-fix.user.prompt", "{{sumary}} {{source}} {{text}} {{findings}}");

        assertThatThrownBy(() -> new PromptTemplates(loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("directed-fix.user.prompt")
                .hasMessageContaining("sumary");
    }

    // A file that forgot a required slot would silently drop the segment text.
    @Test
    void load_requiredSlotMissing_namesFileAndSlot() {
        final var loader = bundledExcept("directed-fix.user.prompt", "{{source}} {{findings}}");

        assertThatThrownBy(() -> new PromptTemplates(loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("directed-fix.user.prompt")
                .hasMessageContaining("text");
    }

    @Test
    void load_missingFile_namesFile() {
        final PromptTemplates.ResourceLoader loader =
                fileName -> fileName.startsWith("languages/") ? BUNDLED.open(fileName) : null;

        assertThatThrownBy(() -> new PromptTemplates(loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("translate.system.prompt");
    }

    @Test
    void renderUser_emptyOptionalTokens_removesWholeBlock() {
        final String user = new PromptTemplates()
                .renderUser(
                        PromptName.DIRECTED_FIX,
                        Map.of("source", "A", "text", "Hi.", "findings", "- language", "expectedTokens", ""));

        assertThat(user).startsWith("<Source>\nA\n</Source>").doesNotContain("Expected tokens", "{{");
    }

    @Test
    void renderUser_filledOptionalTokens_keepsBodyWithoutMarkers() {
        final String user = new PromptTemplates()
                .renderUser(
                        PromptName.DIRECTED_FIX,
                        Map.of("source", "A", "text", "Hi.", "findings", "- language", "expectedTokens", "⟦g0⟧ ⟦g1⟧"));

        assertThat(user)
                .contains(
                        "[Expected tokens — your translation contains exactly this sequence]\n⟦g0⟧ ⟦g1⟧\n\n<Translation>")
                .doesNotContain("{{");
    }

    // A slot value is book text and must be inserted literally, never re-read as a slot or a replacement group.
    @Test
    void renderUser_valueLooksLikeSlotOrReplacement_isInsertedLiterally() {
        final String user = new PromptTemplates()
                .renderUser(
                        PromptName.DIRECTED_FIX, Map.of("source", "A", "text", "{{source}} $1 \\", "findings", "- x"));

        assertThat(user).contains("<Translation>\n{{source}} $1 \\\n</Translation>");
    }

    @Test
    void renderUser_undeclaredValue_isRejected() {
        final var templates = new PromptTemplates();
        final var values = Map.of("nope", "x");
        assertThatThrownBy(() -> templates.renderUser(PromptName.FORMAT_REASK, values))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void renderSystem_callWithoutSystemTemplate_isRejected() {
        final var templates = new PromptTemplates();
        final Map<String, String> values = Map.of();
        assertThatThrownBy(() -> templates.renderSystem(PromptName.FORMAT_REASK, values))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renderUser_directedFix_holdsSourceDefectsAndOneTranslationBlock() {
        final String user = new PromptTemplates()
                .renderUser(
                        PromptName.DIRECTED_FIX,
                        Map.of(
                                "source",
                                "Sam opened the ⟦g0⟧old⟦g1⟧ door.",
                                "text",
                                "Сем відчинив ⟦g0⟧старі⟦g1⟧ двері.",
                                "findings",
                                "- language"));

        assertThat(user)
                .startsWith("<Source>\nSam opened the ⟦g0⟧old⟦g1⟧ door.\n</Source>\n\n[Defects to fix")
                .contains("\n- language\n\n<Translation>\nСем відчинив ⟦g0⟧старі⟦g1⟧ двері.\n</Translation>")
                .endsWith("{\"target\":\"<the full corrected translation>\"}\n");
    }

    @Test
    void renderSystem_directedFix_fillsLanguagesAndStyle() {
        final String system = new PromptTemplates()
                .renderSystem(
                        PromptName.DIRECTED_FIX,
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
                .startsWith("You correct a translation from English (en) into Ukrainian (uk).")
                .contains("Style:\nNeutral register.\n", "6. Keep foreign passages.\n")
                .doesNotContain("{{");
    }

    @Test
    void injector_pipelineModule_loadsEveryTemplate() {
        final var injector = Guice.createInjector(
                new PipelineModule(),
                new ReviewModeTestModule(),
                new CallLedgerTestModule(),
                new DocumentModule(),
                new InMemoryPersistenceModule());

        assertThat(injector.getInstance(PromptTemplates.class)).isNotNull();
    }

    // The eval's generic switch must reach the rendered prompt, or "rules on vs generic" would compare a prompt with
    // itself.
    @Test
    void renderSystem_genericOnlyRules_carryTheGenericSectionAndNoLanguageRule() {
        final PromptTemplates templates = new PromptTemplates(BUNDLED, new LanguageRules(BUNDLED, true));
        final CallFrame frame = new CallFrame(
                "en",
                "uk",
                StyleSheet.from(ua.bookloom.api.project.BookBrief.defaults("en")),
                ua.bookloom.api.project.ForeignPassagePolicy.KEEP);

        assertThat(templates.renderSystem(PromptName.REVIEWER, frame))
                .contains(
                        "[Language rules: English -> Ukrainian]", "Use the quotation marks, dashes and number formats")
                .doesNotContain("«…» for speech", "russisms");
    }

    // An arm chooses the generic rules through the injector's binding, never through a variable src/main reads.
    @Test
    void render_genericOnlyVariant_statesOnlyTheGenericRules() {
        final PromptTemplates templates =
                new PromptTemplates(new PromptVariants(PromptVariants.LanguageRulesVariant.GENERIC_ONLY));
        final CallFrame frame = new CallFrame(
                "en",
                "uk",
                StyleSheet.from(ua.bookloom.api.project.BookBrief.defaults("en")),
                ua.bookloom.api.project.ForeignPassagePolicy.KEEP);

        assertThat(templates.renderSystem(PromptName.REVIEWER, frame))
                .contains(
                        "[Language rules: English -> Ukrainian]", "Use the quotation marks, dashes and number formats")
                .doesNotContain("«…» for speech", "russisms");
    }
}
