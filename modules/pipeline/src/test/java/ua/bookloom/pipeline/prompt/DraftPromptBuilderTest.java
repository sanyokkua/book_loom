package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.FootnotePolicy;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Register;
import ua.bookloom.api.project.UnitPolicy;

/** Verifies the single-segment draft prompt follows the catalog contract. */
class DraftPromptBuilderTest {

    private static final String FULL_CONTEXT_USER_MESSAGE = """
                        [Book so far — context only; do NOT re-translate it]
                        Гейл шукає брата.

                        [Glossary — apply these renderings exactly]
                        Hale → Гейл (character, male)

                        [Earlier decisions — keep consistent]
                        He left. → Він пішов.

                        [Previous translated text — context only; do NOT re-translate it]
                        <PreviousTranslations>
                        Один.

                        Два.
                        </PreviousTranslations>

                        Translate from English (en) to Ukrainian (uk).

                        [Immutable tokens for this text]
                        Copy this exact ordered sequence unchanged: (none; do not invent placeholders)
                        Do not add, reorder, split, translate, or omit these tokens.

                        <Text>
                        Hello.
                        </Text>

                        Return exactly one JSON object matching this schema: {"target":"<translation>"}""";

    // A BCP-47 language pair must reach the model as unambiguous English names plus its raw tags.
    @Test
    void messagesFor_knownLanguages_rendersReadableLanguageDescriptions() {
        final DraftPromptBuilder builder = builder("en", "uk", BookBrief.defaults("en"));

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
        final DraftPromptBuilder builder = builder(null, "uk", BookBrief.defaults(null));

        final String system = builder.messagesFor(segment("Hello.")).getFirst().content();

        assertThat(system)
                .contains("from the language of this segment (infer it from its text) into Ukrainian (uk)")
                .doesNotContain("from the source language into uk");
    }

    // A BCP-47 variant must give the model both the English locale description and exact tag.
    @Test
    void messagesFor_variantLanguageTag_rendersEnglishDescriptionAndTag() {
        final DraftPromptBuilder builder = builder("zh-Hant", "uk", BookBrief.defaults("zh-Hant"));

        final String system = builder.messagesFor(segment("你好。")).getFirst().content();

        assertThat(system).contains("from Chinese (Traditional) (zh-Hant) into Ukrainian (uk)");
    }

    // An unregistered BCP-47 tag must stay explicit instead of being mistaken for a language name.
    @Test
    void messagesFor_unregisteredLanguageTag_rendersQuotedTagFallback() {
        final DraftPromptBuilder builder = builder("en", "qaa", BookBrief.defaults("en"));

        final String system = builder.messagesFor(segment("Hello.")).getFirst().content();

        assertThat(system).contains("into language tag \"qaa\"");
    }

    // One source segment is delimited as prose, while absent context is omitted rather than distracting the model.
    @Test
    void messagesFor_emptyContext_rendersDelimitedSourceAndRepeatedTokenRule() {
        final DraftPromptBuilder builder = builder("en", "uk", BookBrief.defaults("en"));

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
        final DraftPromptBuilder builder = builder("en", "uk", BookBrief.defaults("en"));

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
        final DraftPromptBuilder builder = builder("en", "uk", BookBrief.defaults("en"));

        final String user = builder.messagesFor(segment("Hello."), new DraftContext(List.of("One.", "Two.")))
                .get(1)
                .content();

        assertThat(user)
                .contains("<PreviousTranslations>\nOne.\n\nTwo.\n</PreviousTranslations>")
                .doesNotContain("[Glossary")
                .doesNotContain("[Book so far");
    }

    // The load-bearing items sit at the edges: the rules in the system message, the source last in the user one.
    @Test
    void messagesFor_fullContext_ordersSummaryTermsHintsPrecedingThenTheSourceLast() {
        final DraftPromptBuilder builder = builder("en", "uk", BookBrief.defaults("en"));
        final DraftContext context = new DraftContext(
                List.of("Один.", "Два."),
                "Гейл шукає брата.",
                List.of("Hale → Гейл (character, male)"),
                List.of("He left. → Він пішов."));

        final String user =
                builder.messagesFor(segment("Hello."), context).get(1).content();

        assertThat(user).isEqualTo(FULL_CONTEXT_USER_MESSAGE);
    }

    // A context with nothing in it renders exactly what a draft with no context always did.
    @Test
    void messagesFor_contextWithEmptyParts_rendersTheSameAsNoContext() {
        final DraftPromptBuilder builder = builder("en", "uk", BookBrief.defaults("en"));
        final Segment segment = segment("Hello.");

        final var withEmptyParts =
                builder.messagesFor(segment, new DraftContext(List.of(), null, List.of(), List.of()));

        assertThat(withEmptyParts).isEqualTo(builder.messagesFor(segment));
        assertThat(withEmptyParts.get(1).content())
                .doesNotContain("[Book so far")
                .doesNotContain("[Glossary");
    }

    // Every brief control reaches the model through the system message's style guidance.
    @Test
    void messagesFor_detectiveBrief_systemCarriesEntriesAndPhrases() {
        final BookBrief brief = new BookBrief(
                "en",
                "uk",
                "Detective fiction",
                Register.FORMAL_LITERARY,
                "Victorian, first person",
                "Adults",
                NamePolicy.TRANSLITERATE,
                ForeignPassagePolicy.KEEP,
                FootnotePolicy.TRANSLATE,
                UnitPolicy.METRIC,
                55,
                AlsoTranslate.defaults(),
                QualityDial.BALANCED);

        final String system = builder("en", "uk", brief)
                .messagesFor(segment("Hello."))
                .getFirst()
                .content();

        assertThat(system)
                .contains("Genre: Detective fiction")
                .contains("Narrative voice / era: Victorian, first person")
                .contains("Audience: Adults")
                .contains("Names: transliterate personal and place names")
                .contains("Units: convert measurements given in prose to metric units.");
    }

    @Test
    void messagesFor_translateForeignPolicy_replacesKeepSentence() {
        final BookBrief base = BookBrief.defaults("en");
        final BookBrief brief = new BookBrief(
                "en",
                "uk",
                null,
                base.register(),
                null,
                null,
                base.names(),
                ForeignPassagePolicy.TRANSLATE,
                base.footnotes(),
                base.units(),
                base.balance(),
                base.alsoTranslate(),
                base.dial());

        final String system = builder("en", "uk", brief)
                .messagesFor(segment("Hello."))
                .getFirst()
                .content();

        assertThat(system)
                .doesNotContain("keep it verbatim")
                .contains("Translate any passage written in another language");
    }

    @Test
    void messagesFor_defaultBriefMarkdownToUk_keepsForeignPassagesVerbatim() {
        final String system = builder("en", "uk", BookBrief.defaults("en"))
                .messagesFor(segment("Hello."))
                .getFirst()
                .content();

        assertThat(system).contains("keep it verbatim");
    }

    // An empty instruction adds no block and no blank line: the message is the shipped one, byte for byte.
    @Test
    void messagesFor_emptyExtraInstruction_matchesTheGolden() throws IOException {
        final DraftPromptBuilder builder = builder("en", "uk", BookBrief.defaults("en"));

        final String user = builder.messagesFor(
                        segment("He opened the ⟦g0⟧old⟦g1⟧ door.", Map.of("g0", "*", "g1", "*")),
                        DraftContext.empty(),
                        "He opened the ⟦g0⟧old⟦g1⟧ door.",
                        "")
                .get(1)
                .content();

        assertThat(user).isEqualTo(golden("draft-en-uk.user.txt"));
    }

    // The instruction sits directly above the text, after the token rule, so the source is still the last thing read.
    @Test
    void messagesFor_extraInstruction_rendersItsBlockBetweenTheTokenRuleAndTheText() {
        final DraftPromptBuilder builder = builder("en", "uk", BookBrief.defaults("en"));

        final String user = builder.messagesFor(
                        segment("Hello."), DraftContext.empty(), "Hello.", "echo similarity 1.0")
                .get(1)
                .content();

        assertThat(user).contains("""
                Do not add, reorder, split, translate, or omit these tokens.

                [Extra instruction]
                echo similarity 1.0

                <Text>
                Hello.
                </Text>""");
    }

    // A repair of a draft that carried an instruction still shows that instruction, so the model keeps being told it.
    @Test
    void messagesForPlaceholderRepair_extraInstruction_keepsItInTheOriginalDraftPrompt() {
        final DraftPromptBuilder builder = builder("en", "uk", BookBrief.defaults("en"));

        final String user = builder.messagesForPlaceholderRepair(
                        segment("Hello."), DraftContext.empty(), "Hello.", "keep it short", "Привіт.", null)
                .get(1)
                .content();

        assertThat(user).contains("[Extra instruction]\nkeep it short\n\n<Text>");
    }

    // A repair the gate gave no rule for is byte-identical to the shipped prompt: no empty block, no stray blank line.
    @Test
    void messagesForPlaceholderRepair_noNote_matchesTheGolden() throws IOException {
        final DraftPromptBuilder builder = builder("en", "uk", BookBrief.defaults("en"));

        final String user = builder.messagesForPlaceholderRepair(
                        segment("He opened the ⟦g0⟧old⟦g1⟧ door.", Map.of("g0", "*", "g1", "*")),
                        DraftContext.empty(),
                        "Він відчинив ⟦g0⟧старі двері.",
                        null)
                .get(1)
                .content();

        assertThat(user).isEqualTo(golden("placeholder-repair.user.txt"));
    }

    // The rule the gate refused is named in its own block, after the required sequence and before the correction.
    @Test
    void messagesForPlaceholderRepair_withNote_namesTheRuleBeforeTheCorrectionLine() {
        final DraftPromptBuilder builder = builder("en", "uk", BookBrief.defaults("en"));

        final String user = builder.messagesForPlaceholderRepair(
                        segment("He opened the ⟦g0⟧old⟦g1⟧ door.", Map.of("g0", "*", "g1", "*")),
                        DraftContext.empty(),
                        "Він відчинив ⟦g0⟧⟦g1⟧ двері.",
                        "a pair of placeholders no longer wraps the same text")
                .get(1)
                .content();

        assertThat(user).contains("""
                Copy this exact ordered sequence unchanged: ⟦g0⟧ ⟦g1⟧
                [Rule the rejected target broke]
                a pair of placeholders no longer wraps the same text
                Correct the target from <Text>""");
    }

    private static String golden(final String file) throws IOException {
        try (var stream = DraftPromptBuilderTest.class.getResourceAsStream("golden/" + file)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static DraftPromptBuilder builder(
            @Nullable final String source, final String target, final BookBrief brief) {
        return new DraftPromptBuilder(
                new PromptTemplates(), new CallFrame(source, target, StyleSheet.from(brief), brief.foreignPassages()));
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
