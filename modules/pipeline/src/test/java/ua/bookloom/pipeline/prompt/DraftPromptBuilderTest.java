package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;

/** Verifies the single-segment draft prompt follows the catalog contract. */
class DraftPromptBuilderTest {

    // A BCP-47 language pair must reach the model as unambiguous English names plus its raw tags.
    @Test
    void messagesFor_knownLanguages_rendersReadableLanguageDescriptions() {
        final DraftPromptBuilder builder = new DraftPromptBuilder("en", "uk");

        final var messages = builder.messagesFor(segment("He opened the ⟦g0⟧old⟦g1⟧ door."));

        assertThat(messages.getFirst().content())
                .contains("translating from English (en) into Ukrainian (uk)")
                .contains("⟦gN⟧ EXACTLY as written")
                .contains("Paired placeholders must enclose nonblank translated text")
                .contains("Output ONLY the required JSON object");
        assertThat(messages.get(1).content()).contains("from English (en) to Ukrainian (uk)");
    }

    // An absent source language must tell the model to infer the segment language rather than leave it vague.
    @Test
    void messagesFor_unknownSourceLanguage_instructsModelToInferItFromSegmentText() {
        final DraftPromptBuilder builder = new DraftPromptBuilder(null, "uk");

        final String system = builder.messagesFor(segment("Hello.")).getFirst().content();

        assertThat(system)
                .contains("from the language of this segment (infer it from its text) into Ukrainian (uk)")
                .doesNotContain("from the source language into uk");
    }

    // A BCP-47 variant must give the model both the English locale description and exact tag.
    @Test
    void messagesFor_variantLanguageTag_rendersEnglishDescriptionAndTag() {
        final DraftPromptBuilder builder = new DraftPromptBuilder("zh-Hant", "uk");

        final String system = builder.messagesFor(segment("你好。")).getFirst().content();

        assertThat(system).contains("from Chinese (Traditional) (zh-Hant) into Ukrainian (uk)");
    }

    // An unregistered BCP-47 tag must stay explicit instead of being mistaken for a language name.
    @Test
    void messagesFor_unregisteredLanguageTag_rendersQuotedTagFallback() {
        final DraftPromptBuilder builder = new DraftPromptBuilder("en", "qaa");

        final String system = builder.messagesFor(segment("Hello.")).getFirst().content();

        assertThat(system).contains("into language tag \"qaa\"");
    }

    // One source segment is delimited as prose, while absent context is omitted rather than distracting the model.
    @Test
    void messagesFor_emptyContext_rendersDelimitedSourceAndRepeatedTokenRule() {
        final DraftPromptBuilder builder = new DraftPromptBuilder("en", "uk");

        final String user = builder.messagesFor(segment("He opened the ⟦g0⟧old⟦g1⟧ door."), DraftContext.empty())
                .get(1)
                .content();

        assertThat(user)
                .contains("Copy this exact ordered sequence unchanged: ⟦g0⟧ ⟦g1⟧")
                .contains("<Text>\nHe opened the ⟦g0⟧old⟦g1⟧ door.\n</Text>")
                .contains("{\"target\":\"<translation>\"}")
                .doesNotContain("(none)")
                .doesNotContain("\"id\"")
                .doesNotContain("\"segments\"");
    }

    // The system teaches placeholder placement structurally without biasing the requested target language.
    @Test
    void messagesFor_anyLanguage_rendersLanguageNeutralPlaceholderShots() {
        final DraftPromptBuilder builder = new DraftPromptBuilder("en", "uk");

        final String system = builder.messagesFor(segment("Hello."), DraftContext.empty())
                .getFirst()
                .content();

        assertThat(system)
                .contains("A ⟦g0⟧B⟦g1⟧ C → X ⟦g0⟧Y⟦g1⟧ Z")
                .contains("A ⟦g0⟧B⟦g1⟧ C ⟦g2⟧D⟦g3⟧ → X ⟦g0⟧Y⟦g1⟧ Z ⟦g2⟧W⟦g3⟧")
                .contains("https://example.test/a")
                .contains("{\"target\":\"X ⟦g0⟧Y⟦g1⟧ Z\"}")
                .contains("structural only");
    }

    // Previous accepted targets are the only context rendered today and retain their document order.
    @Test
    void messagesFor_precedingTargets_rendersOnlyDelimitedContext() {
        final DraftPromptBuilder builder = new DraftPromptBuilder("en", "uk");

        final String user = builder.messagesFor(segment("Hello."), new DraftContext(List.of("One.", "Two.")))
                .get(1)
                .content();

        assertThat(user)
                .contains("<PreviousTranslations>\nOne.\n\nTwo.\n</PreviousTranslations>")
                .doesNotContain("[Glossary")
                .doesNotContain("[Book so far");
    }

    private static Segment segment(final String masked) {
        return segment(masked, Map.of());
    }

    private static Segment segment(final String masked, final Map<String, String> placeholders) {
        return new Segment(
                "Book.md:0",
                "Book.md",
                0,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                placeholders,
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, masked.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }
}
