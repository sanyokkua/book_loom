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
        final var loader = bundledExcept("revision.user.prompt", "{{sumary}} {{source}} {{text}}");

        assertThatThrownBy(() -> new PromptTemplates(loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revision.user.prompt")
                .hasMessageContaining("sumary");
    }

    // A file that forgot a required slot would silently drop the segment text.
    @Test
    void load_requiredSlotMissing_namesFileAndSlot() {
        final var loader = bundledExcept("revision.user.prompt", "{{source}} {{tokens}}");

        assertThatThrownBy(() -> new PromptTemplates(loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revision.user.prompt")
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
    void renderUser_emptyOptionalFacts_removesWholeBlock() {
        final String user = new PromptTemplates()
                .renderUser(
                        PromptName.REVISION, Map.of("source", "A", "text", "Hi.", "tokens", "", "resolvedFacts", ""));

        assertThat(user).startsWith("<Source>\nA\n</Source>").doesNotContain("Resolved facts", "{{");
    }

    @Test
    void renderUser_filledOptionalFacts_keepsBodyWithoutMarkers() {
        final String user = new PromptTemplates()
                .renderUser(PromptName.REVISION, Map.of("source", "A", "text", "Hi.", "resolvedFacts", "It began."));

        assertThat(user)
                .startsWith("[Resolved facts revealed later in the book]\nIt began.\n\n<Source>")
                .doesNotContain("{{");
    }

    // A slot value is book text and must be inserted literally, never re-read as a slot or a replacement group.
    @Test
    void renderUser_valueLooksLikeSlotOrReplacement_isInsertedLiterally() {
        final String user = new PromptTemplates()
                .renderUser(PromptName.REVISION, Map.of("source", "A", "text", "{{source}} $1 \\"));

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

    // The revision call holds one <Translation> block with the masked target; the masked source sits in <Source>.
    @Test
    void renderUser_revisionWithoutFacts_holdsSourceAndOneTranslationBlock() {
        final String user = new PromptTemplates()
                .renderUser(
                        PromptName.REVISION,
                        Map.of(
                                "source",
                                "Sam opened the ⟦g0⟧old⟦g1⟧ door.",
                                "text",
                                "Сем відчинив ⟦g0⟧старі⟦g1⟧ двері."));

        assertThat(user).isEqualTo("""
                        <Source>
                        Sam opened the ⟦g0⟧old⟦g1⟧ door.
                        </Source>

                        <Translation>
                        Сем відчинив ⟦g0⟧старі⟦g1⟧ двері.
                        </Translation>

                        Return one JSON object: {"target":"<the revised translation, or the same text if nothing changes>"}
                        """);
    }

    // A source with tokens lists them for the rewrite, as the draft does.
    @Test
    void renderUser_revisionWithTokens_listsThemBeforeTheTranslation() {
        final String user = new PromptTemplates()
                .renderUser(
                        PromptName.REVISION,
                        Map.of(
                                "source", "Sam opened the ⟦g0⟧old⟦g1⟧ door.",
                                "text", "Сем відчинив ⟦g0⟧старі⟦g1⟧ двері.",
                                "tokens", "⟦g0⟧ ⟦g1⟧"));

        assertThat(user)
                .contains("[Immutable tokens]\nCopy this exact ordered sequence unchanged: ⟦g0⟧ ⟦g1⟧\n\n<Translation>");
    }

    @Test
    void renderUser_revisionWithFacts_namesThemBeforeTheSource() {
        final String user = new PromptTemplates()
                .renderUser(
                        PromptName.REVISION,
                        Map.of("source", "Sam left.", "text", "Сем пішов.", "resolvedFacts", "- Sam (Сем): female"));

        assertThat(user)
                .startsWith(
                        "[Resolved facts revealed later in the book]\n- Sam (Сем): female\n\n<Source>\nSam left.\n");
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
                .startsWith("You revise one segment of a book already translated from English (en) into Ukrainian (uk)")
                .contains("Style:\nNeutral register.\n", "5. Keep foreign passages.\n")
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
