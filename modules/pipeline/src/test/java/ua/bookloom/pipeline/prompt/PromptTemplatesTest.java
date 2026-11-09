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
        final var loader = bundledExcept("repair.user.prompt", "{{sumary}} {{defects}}");

        assertThatThrownBy(() -> new PromptTemplates(loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("repair.user.prompt")
                .hasMessageContaining("sumary");
    }

    // A file that forgot a required slot would silently drop the segment text.
    @Test
    void load_requiredSlotMissing_namesFileAndSlot() {
        final var loader = bundledExcept("format-reask.user.prompt", "{{rejectedReply}}");

        assertThatThrownBy(() -> new PromptTemplates(loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("format-reask.user.prompt")
                .hasMessageContaining("diagnostic");
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
                .renderUser(PromptName.REPAIR, Map.of("defects", "- language", "previousAttempt", ""));

        assertThat(user).startsWith("[Defects to fix]").doesNotContain("Previous attempt", "{{");
    }

    @Test
    void renderUser_filledOptionalTokens_keepsBodyWithoutMarkers() {
        final String user = new PromptTemplates()
                .renderUser(PromptName.REPAIR, Map.of("defects", "- language", "previousAttempt", "Old text."));

        assertThat(user)
                .contains("<PreviousAttempt>\nOld text.\n</PreviousAttempt>")
                .doesNotContain("{{");
    }

    // A slot value is book text and must be inserted literally, never re-read as a slot or a replacement group.
    @Test
    void renderUser_valueLooksLikeSlotOrReplacement_isInsertedLiterally() {
        final String user = new PromptTemplates().renderUser(PromptName.REPAIR, Map.of("defects", "{{defects}} $1 \\"));

        assertThat(user).contains("[Defects to fix]\n{{defects}} $1 \\\n");
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

        assertThat(templates.languageRules().section("en", "uk"))
                .contains(
                        "[Language rules: English -> Ukrainian]", "Use the quotation marks, dashes and number formats")
                .doesNotContain("«…» for speech", "russisms");
    }

    // An arm chooses the generic rules through the injector's binding, never through a variable src/main reads.
    @Test
    void render_genericOnlyVariant_statesOnlyTheGenericRules() {
        final PromptTemplates templates =
                new PromptTemplates(new PromptVariants(PromptVariants.LanguageRulesVariant.GENERIC_ONLY));

        assertThat(templates.languageRules().section("en", "uk"))
                .contains(
                        "[Language rules: English -> Ukrainian]", "Use the quotation marks, dashes and number formats")
                .doesNotContain("«…» for speech", "russisms");
    }
}
