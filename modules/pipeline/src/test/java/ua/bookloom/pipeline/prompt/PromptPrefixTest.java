package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.batch.BatchContext;
import ua.bookloom.pipeline.batch.BatchFormat;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.batch.BatchPromptBuilder;

/**
 * A local server reuses a prompt's cached prefix only up to the first byte that differs, so every call kind sends its
 * fixed blocks first and unchanged; only the token rules, which exist for some calls, come last.
 */
class PromptPrefixTest {

    private static final PromptTemplates TEMPLATES = new PromptTemplates();
    private static final String TOKEN_RULES_HEADING = "⟦gN⟧ tokens stand for";

    private static CallFrame frame() {
        final BookBrief brief = BookBrief.defaults("en");
        return new CallFrame("en", "uk", StyleSheet.from(brief), ForeignPassagePolicy.KEEP);
    }

    @Test
    void draftSystem_withAndWithoutTokens_shareEverythingBeforeTheTokenRules() {
        final DraftPromptBuilder builder = new DraftPromptBuilder(TEMPLATES, frame());

        final String plain = builder.messagesFor(GoldenCases.segment("He opened the old door."))
                .getFirst()
                .content();
        final String tokens = builder.messagesFor(GoldenCases.segment("He opened the ⟦g0⟧old⟦g1⟧ door."))
                .getFirst()
                .content();

        assertThat(tokens).startsWith(beforeOutputLine(plain)).contains(TOKEN_RULES_HEADING);
        assertThat(plain).doesNotContain(TOKEN_RULES_HEADING);
    }

    @Test
    void draftSystem_twoDifferentSegments_areByteIdentical() {
        final DraftPromptBuilder builder = new DraftPromptBuilder(TEMPLATES, frame());

        final String first =
                builder.messagesFor(GoldenCases.segment("One door.")).getFirst().content();
        final String second = builder.messagesFor(GoldenCases.segment("A much later paragraph."))
                .getFirst()
                .content();

        assertThat(second).isEqualTo(first);
    }

    @ParameterizedTest
    @EnumSource(
            value = BatchFormat.class,
            names = {"JSON", "DUAL", "MARKERS"})
    void batchSystem_withAndWithoutTokens_shareEverythingBeforeTheTokenRules(final BatchFormat format) {
        final BatchPromptBuilder builder = new BatchPromptBuilder(TEMPLATES, frame(), format, false);

        final String plain = system(builder, new BatchItem("1", "She opened the door."), new BatchItem("2", "1881"));
        final String tokens =
                system(builder, new BatchItem("1", "She opened the door."), new BatchItem("2", "⟦g0⟧Hi⟦g1⟧."));

        assertThat(tokens).startsWith(beforeOutputLine(plain)).contains(TOKEN_RULES_HEADING);
        assertThat(plain).doesNotContain(TOKEN_RULES_HEADING);
    }

    @ParameterizedTest
    @EnumSource(
            value = BatchFormat.class,
            names = {"JSON", "DUAL", "MARKERS"})
    void batchSystem_twoDifferentBatches_areByteIdentical(final BatchFormat format) {
        final BatchPromptBuilder builder = new BatchPromptBuilder(TEMPLATES, frame(), format, false);

        final String first = system(builder, new BatchItem("1", "One."), new BatchItem("2", "Two."));
        final String second = system(builder, new BatchItem("7", "Seven."), new BatchItem("8", "Eight."));

        assertThat(second).isEqualTo(first);
    }

    // Every other kind renders its system message from the run's frame alone (the callers pass no per-call slot), so
    // two calls of a run cannot differ; this pins that no per-call placeholder survives into the text.
    @ParameterizedTest
    @EnumSource(PromptName.class)
    void system_anyKind_isFixedForTheRunAndHoldsNoOpenSlot(final PromptName name) {
        assumeThat(name.systemSlots()).isPresent();
        final Map<String, String> extra = name == PromptName.SUGGEST_TARGETS ? Map.of("nameRule", "rule") : Map.of();

        final String first = TEMPLATES.renderSystem(name, frame(), extra);
        final String second = TEMPLATES.renderSystem(name, frame(), extra);

        assertThat(second).isEqualTo(first).doesNotContain("{{", "}}");
    }

    private static String system(final BatchPromptBuilder builder, final BatchItem... items) {
        return builder.messagesFor(BatchContext.empty(), List.of(items))
                .getFirst()
                .content();
    }

    private static String beforeOutputLine(final String noTokenSystem) {
        return noTokenSystem.substring(0, noTokenSystem.lastIndexOf("Output ONLY"));
    }
}
