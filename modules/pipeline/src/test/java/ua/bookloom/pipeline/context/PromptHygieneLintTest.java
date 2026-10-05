package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.context.PromptLint.Facts;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.NameRules;
import ua.bookloom.pipeline.prompt.PromptBreakdown;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * The no-garbage rule over really assembled prompts: every call kind's templates rendered with realistic values, and
 * the draft prompt built through the real context assembler, must carry no empty heading, no repeated line, no
 * placeholder, no glossary term the chunk lacks and no text from another chapter.
 */
class PromptHygieneLintTest {

    private static final PromptTemplates TEMPLATES = new PromptTemplates();
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final String SOURCE = "Hale and Moreau opened the old door.";
    private static final String TARGET = "Гейл і Моро відчинили старі двері.";
    private static final Facts SAMPLE_FACTS =
            new Facts(SOURCE + " ⟦g0⟧ ⟦g1⟧", List.of("Один."), List.of("Старий розділ."));

    private static final Map<String, String> SAMPLES = Map.ofEntries(
            Map.entry("source", SOURCE),
            Map.entry("target", TARGET),
            Map.entry("tokens", "⟦g0⟧ ⟦g1⟧"),
            Map.entry("expectedTokens", "⟦g0⟧ ⟦g1⟧"),
            Map.entry("summary", "Hale searches for his brother."),
            Map.entry("glossaryTerms", "Hale → Гейл (character, male)"),
            Map.entry("lockedNames", "⟦g0⟧ → Гейл, character, male"),
            Map.entry("suggestedTerms", "Moreau → Моро (character, male)"),
            Map.entry("memoryHint", "He left. → Він пішов."),
            Map.entry("precedingTargets", "Один."),
            Map.entry("extraInstruction", "Keep the dialogue tone."),
            Map.entry("rejectedReply", "not json"),
            Map.entry("diagnostic", "the reply is not a JSON object"),
            Map.entry("rejectedTarget", "Гейл і Моро відчинили двері."),
            Map.entry("gateNote", "a token was dropped"),
            Map.entry(
                    "pairs",
                    "<Pair id=\"s1\"><Source>" + SOURCE + "</Source><Candidate>" + TARGET + "</Candidate></Pair>"),
            Map.entry("findings", "omission: the word old is missing"),
            Map.entry("issues", "The register is too formal."),
            Map.entry("resolvedFacts", "Hale is a woman."),
            Map.entry("candidates", "Hale — Hale and Moreau opened the old door."),
            Map.entry("sourceLanguage", "English (en)"),
            Map.entry("targetLanguage", "Ukrainian (uk)"),
            Map.entry("existingTerms", "Milton"),
            Map.entry("terms", "Hale (3 uses) — Hale and Moreau opened the old door."),
            Map.entry("chapterSource", SOURCE),
            Map.entry("chapterTarget", TARGET),
            Map.entry("previousSummary", "Hale searches for his brother."),
            Map.entry("items", "<s id=\"1\">" + SOURCE + " ⟦g0⟧ ⟦g1⟧</s>"),
            Map.entry("itemTokens", "1: ⟦g0⟧ ⟦g1⟧"),
            Map.entry("characters", "Hale — a man"),
            Map.entry("precedingPairs", "Source: He left.\nTranslation: Він пішов."),
            Map.entry("nextSource", "He came back."));

    @ParameterizedTest
    @EnumSource(PromptName.class)
    void render_everyCallWithEveryOptionalBlock_passesTheLint(final PromptName name) {
        final List<ChatMessage> messages = messages(name, true);

        assertThat(PromptLint.violations(text(messages), SAMPLE_FACTS)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(PromptName.class)
    void render_everyCallWithRequiredSlotsOnly_passesTheLint(final PromptName name) {
        final List<ChatMessage> messages = messages(name, false);

        assertThat(PromptLint.violations(text(messages), SAMPLE_FACTS)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = PromptName.class,
            names = {"STRUCTURAL_REPAIR", "PLACEHOLDER_REPAIR"},
            mode = EnumSource.Mode.EXCLUDE)
    void staticPrefix_everyCall_isTheSameBytesForTwoCalls(final PromptName name) {
        final String first = system(name);
        final String second = system(name);

        assertThat(first).isEqualTo(second);
    }

    // A language file with an empty heading, a repeated rule or a placeholder would reach every call of its pair.
    @ParameterizedTest
    @MethodSource("ua.bookloom.pipeline.prompt.V1Languages#languages")
    void render_languageRulesOfEveryV1Language_passTheLintAsTargetAndAsSource(final String tag) {
        final CallFrame asTarget =
                new CallFrame("en", tag, StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
        final CallFrame asSource =
                new CallFrame(tag, "uk", StyleSheet.from(BookBrief.defaults(tag)), ForeignPassagePolicy.KEEP);

        assertThat(PromptLint.violations(TEMPLATES.renderSystem(PromptName.DRAFT, asTarget), SAMPLE_FACTS))
                .isEmpty();
        assertThat(PromptLint.violations(TEMPLATES.renderSystem(PromptName.JUDGE, asTarget), SAMPLE_FACTS))
                .isEmpty();
        assertThat(PromptLint.violations(TEMPLATES.renderSystem(PromptName.JUDGE, asSource), SAMPLE_FACTS))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("ua.bookloom.pipeline.prompt.V1Languages#pairs")
    void render_languageRulesOfEveryPair_passTheLint(final String pair) {
        final int dash = pair.indexOf('-');
        final CallFrame frame = new CallFrame(
                pair.substring(0, dash),
                pair.substring(dash + 1),
                StyleSheet.from(BookBrief.defaults(pair.substring(0, dash))),
                ForeignPassagePolicy.KEEP);

        assertThat(PromptLint.violations(TEMPLATES.renderSystem(PromptName.JUDGE, frame), SAMPLE_FACTS))
                .isEmpty();
    }

    @Test
    void render_draftBuiltThroughTheAssembler_passesTheLint() {
        final List<String> earlier = List.of("Один.", "Два.");
        final Chunk chunk = ContextFixtures.chunk("Hale opened the door.", "Baker Street was dark.");
        final Segment segment = chunk.segments().getFirst();
        final List<GlossaryEntry> glossary = List.of(
                ContextFixtures.entry("Hale", "Гейл", TermType.CHARACTER, Gender.MALE, false),
                ContextFixtures.entry("Milton", "Мілтон", TermType.PLACE, Gender.UNKNOWN, false),
                ContextFixtures.entry("Moreau", null, TermType.CHARACTER, Gender.MALE, false));
        final ContextPackage assembled = ContextPackageAssembler.assemble(
                chunk,
                segment,
                ContextFixtures.mask(segment, glossary),
                ContextFixtures.NO_MEMORY,
                ContextFixtures.inputs("Hale searches for his brother.", 2, glossary, earlier));

        final List<ChatMessage> messages = new DraftPromptBuilder(TEMPLATES, FRAME)
                .messagesFor(segment, assembled.draftContext(), segment.masked());

        assertThat(text(messages)).contains("Гейл").doesNotContain("Мілтон", "Moreau");
        assertThat(PromptLint.violations(
                        text(messages), new Facts(segment.masked(), earlier, List.of("Старий розділ."))))
                .isEmpty();
    }

    @Test
    void violations_emptyHeading_isFound() {
        assertThat(PromptLint.violations("[Glossary]\n\n[Summary]\nText here that is long enough.", SAMPLE_FACTS))
                .anyMatch(violation -> violation.startsWith("empty heading: [Glossary]"));
    }

    @Test
    void violations_repeatedLine_isFound() {
        assertThat(PromptLint.violations("Keep the names as they are.\nKeep the names as they are.", SAMPLE_FACTS))
                .anyMatch(violation -> violation.startsWith("duplicate line"));
    }

    @Test
    void violations_noneFiller_isFound() {
        assertThat(PromptLint.violations("[Glossary]\n(none)", SAMPLE_FACTS))
                .anyMatch(violation -> violation.startsWith("filler line: (none)"));
    }

    @Test
    void violations_glossaryTermAbsentFromTheChunk_isFound() {
        assertThat(PromptLint.violations("[Glossary — apply]\nMilton → Мілтон (place, unknown)", SAMPLE_FACTS))
                .anyMatch(violation -> violation.startsWith("glossary term not in the chunk"));
    }

    @Test
    void violations_previousChapterText_isFound() {
        final Facts facts = new Facts(SOURCE, List.of("Один."), List.of("Старий розділ."));

        assertThat(PromptLint.violations(
                        "<PreviousTranslations>\nОдин.\n\nСтарий розділ.\n</PreviousTranslations>", facts))
                .anyMatch(violation -> violation.startsWith("previous text not from this chapter: Старий"));
    }

    // Writes the static prefix's share of each call kind's prompt, the measurement the DEBUG breakdown also gives.
    @Test
    void prefixShare_everyCallKind_isReportedAndNeverEmpty() {
        final List<String> report = new ArrayList<>();
        for (final PromptName name : PromptName.values()) {
            final PromptBreakdown breakdown = PromptBreakdown.of(messages(name, true));
            report.add(name.callKind() + " " + breakdown.describe());
            assertThat(breakdown.total()).as(name.toString()).isPositive();
        }
        write(report);
    }

    private static void write(final List<String> report) {
        try {
            Files.createDirectories(Path.of("build"));
            Files.write(Path.of("build", "prompt-prefix-share.txt"), report);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String text(final List<ChatMessage> messages) {
        return String.join("\n", messages.stream().map(ChatMessage::content).toList());
    }

    private static List<ChatMessage> messages(final PromptName name, final boolean withOptional) {
        final List<ChatMessage> messages = new ArrayList<>();
        if (name.systemSlots().isPresent()) {
            messages.add(new ChatMessage(ChatRole.SYSTEM, system(name).strip()));
        }
        messages.add(new ChatMessage(ChatRole.USER, user(name, withOptional).strip()));
        return messages;
    }

    private static String system(final PromptName name) {
        return name == PromptName.SUGGEST_TARGETS
                ? TEMPLATES.renderSystem(
                        name, FRAME, Map.of("nameRule", NameRules.bundled().rule(NamePolicy.TRANSLITERATE, "uk")))
                : TEMPLATES.renderSystem(name, FRAME);
    }

    private static String user(final PromptName name, final boolean withOptional) {
        final Map<String, String> values = new HashMap<>();
        name.userSlots().required().forEach(slot -> values.put(slot, sample(name, slot)));
        if (withOptional) {
            name.userSlots().optional().forEach(slot -> values.put(slot, sample(name, slot)));
        }
        return TEMPLATES.renderUser(name, values);
    }

    private static String sample(final PromptName name, final String slot) {
        if ("text".equals(slot)) {
            return name == PromptName.DRAFT ? SOURCE : TARGET;
        }
        final String value = SAMPLES.get(slot);
        assertThat(value).as("a realistic sample for slot %s", slot).isNotNull();
        return value;
    }
}
