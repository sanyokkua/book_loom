package ua.bookloom.pipeline.context;

import com.google.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.narrator.NarratorDetector;
import ua.bookloom.pipeline.prompt.FrameWording;
import ua.bookloom.pipeline.prompt.LanguageRules;
import ua.bookloom.pipeline.prompt.PairOutputRatio;
import ua.bookloom.pipeline.prompt.PromptLanguages;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.setup.BookFacts;
import ua.bookloom.util.hash.HashUtil;

/**
 * Builds a run's fixed frame once at run start (design D8, D9): the rule facts, the book rules, the style sheet, the
 * examples and the rendered system blocks, with the hash that names them.
 */
@Slf4j
public final class RunFrames {

    private static final String BULLET = "- ";
    private static final String SLOT_END = "\n";
    private static final String BLOCK_SEPARATOR = "\u0000";

    private final PromptTemplates templates;
    private final LanguageRules rules;
    private final BookRules bookRules;
    private final BlockWords words = BlockWords.bundled();

    /**
     * Reads the catalogue's translate prompt and the language rules its variants chose, as {@link BookRules} does.
     *
     * @param templates the non-null prompt catalogue, whose translate system template holds block 1
     * @param bookRules the non-null chooser of block 3's lines
     */
    @Inject
    public RunFrames(final PromptTemplates templates, final BookRules bookRules) {
        this.templates = Objects.requireNonNull(templates, "templates");
        this.rules = templates.languageRules();
        this.bookRules = Objects.requireNonNull(bookRules, "bookRules");
    }

    /**
     * Builds the frame of one run.
     *
     * @param brief the non-null Book Brief the run starts with
     * @param document the non-null opened book
     * @param index the non-null index of the book
     * @param map the non-null book map at run start
     * @param profile the non-null profile of the run's model
     * @return the frame, or {@link ErrorCode#validation} when the brief has no target language, or
     *     {@link ErrorCode#internal} when a language file cannot be read
     */
    public Result<RunFrame> build(
            final BookBrief brief,
            final Document document,
            final BookIndex index,
            final BookMap map,
            final ModelProfile profile) {
        Objects.requireNonNull(brief, "brief");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(profile, "profile");
        final String target = brief.targetLanguage();
        if (target == null) {
            log.debug("Run frame of {}: no target language", document.id());
            return Result.err(AppError.of(
                    ErrorCode.validation, "No target language", "Choose the language to translate the book into."));
        }
        try {
            return Result.ok(frame(brief, target, document, index, map, profile));
        } catch (RuntimeException cause) {
            log.error("Run frame of {} could not be built", document.id(), cause);
            return Result.err(AppError.of(
                    ErrorCode.internal,
                    "Prompt frame failed",
                    "The run's fixed prompt could not be built.",
                    null,
                    cause));
        }
    }

    private RunFrame frame(
            final BookBrief brief,
            final String target,
            final Document document,
            final BookIndex index,
            final BookMap map,
            final ModelProfile profile) {
        final RuleFacts facts = RuleFacts.of(index, document, brief, map, rules);
        final List<RuleLine> lines = bookRules.choose(facts, target);
        final StyleSheet sheet = StyleSheet.from(brief);
        final ExampleChoice examples = Examples.choose(rules, brief.sourceLanguage(), target, profile);
        final List<Block> system = systemBlocks(brief, target, lines, sheet, examples);
        final RunFrame frame = new RunFrame(
                brief,
                BookFacts.of(document),
                index,
                facts,
                sheet,
                lines,
                examples,
                reminders(brief.narrator(), target),
                PairOutputRatio.of(rules, brief.sourceLanguage(), target).orElse(OutputReserve.DEFAULT_OUTPUT_RATIO),
                system,
                AnalysisFrame.of(brief.sourceLanguage()).blocks(),
                hashOf(system),
                firstPersonUnits(brief, index));
        logFrame(frame);
        return frame;
    }

    // A chapter whose own text is told as "I" gets the narrator line on its card; read once for the whole book, since a
    // card is drawn for every call. A narrator the brief names is the style sheet's, so nothing is read then.
    private static Set<String> firstPersonUnits(final BookBrief brief, final BookIndex index) {
        if (brief.narrator().person() != NarratorPerson.UNSPECIFIED) {
            return Set.of();
        }
        final Map<String, List<Integer>> byUnit = new LinkedHashMap<>();
        index.story()
                .forEach(
                        ordinal -> byUnit.computeIfAbsent(index.segment(ordinal).unitId(), unit -> new ArrayList<>())
                                .add(ordinal));
        final List<String> units = List.copyOf(byUnit.keySet());
        final List<List<String>> chapters =
                byUnit.values().stream().map(index::visibleTexts).toList();
        final Set<String> first = NarratorDetector.detect(chapters, brief.sourceLanguage())
                .map(hint -> hint.chapters().stream()
                        .filter(position -> position >= 1 && position <= units.size())
                        .map(position -> units.get(position - 1))
                        .collect(Collectors.toUnmodifiableSet()))
                .orElse(Set.of());
        log.debug("Run frame first-person units={} of {}", first, units.size());
        return first;
    }

    private static void logFrame(final RunFrame frame) {
        log.info(
                "run frame hash={} rules={} examples={} outputRatio={}",
                frame.frameHash(),
                frame.rules().stream().map(RuleLine::rule).toList(),
                frame.examples().kind().name().toLowerCase(Locale.ROOT),
                frame.outputRatio());
        if (log.isTraceEnabled()) {
            frame.system().forEach(block -> log.trace("Run frame block {}: {}", block.slot(), block.text()));
        }
    }

    private List<Block> systemBlocks(
            final BookBrief brief,
            final String target,
            final List<RuleLine> lines,
            final StyleSheet sheet,
            final ExampleChoice examples) {
        final List<Block> blocks = new ArrayList<>();
        blocks.add(system(
                BlockSlots.ROLE,
                templates
                        .renderSystem(PromptName.TRANSLATE, languageSlots(brief.sourceLanguage(), target))
                        .strip()));
        blocks.add(system(BlockSlots.DATA, words.get(BlockWords.DATA)));
        if (!lines.isEmpty()) {
            blocks.add(system(BlockSlots.BOOK_RULES, words.get(BlockWords.RULES_HEADING) + "\n" + bullets(lines)));
        }
        // The foreign-passage choice of the brief is a style line too, so every call of the run carries it.
        final String foreign = StyleSheet.foreignPassageRule(
                brief.foreignPassages(), PromptLanguages.describe(brief.sourceLanguage()));
        blocks.add(system(
                BlockSlots.STYLE_SHEET, words.get(BlockWords.STYLE_HEADING) + "\n" + sheet.text() + "\n" + foreign));
        final String section =
                FrameWording.section(rules, brief.sourceLanguage(), target, BookRules.sectionExclusions());
        if (!section.isBlank()) {
            blocks.add(system(BlockSlots.LANGUAGE_RULES, section));
        }
        switch (examples.kind()) {
            case PAIR ->
                blocks.add(system(BlockSlots.EXAMPLES, words.get(BlockWords.EXAMPLES_PAIR) + "\n" + examples.text()));
            case TARGET -> blocks.add(system(BlockSlots.EXAMPLES, examples.text()));
            case NONE -> log.debug("Run frame: no examples block");
        }
        blocks.add(system(BlockSlots.REPLY, words.get(BlockWords.REPLY)));
        return List.copyOf(blocks);
    }

    // A likely gender is a guess nobody confirmed and orders no form (15i.T5), so only a stated one is reminded.
    private RunFrame.ReminderWords reminders(final Narrator narrator, final String target) {
        final boolean stated = narrator.person() == NarratorPerson.FIRST
                && (narrator.gender() == Gender.MALE || narrator.gender() == Gender.FEMALE)
                && !narrator.hasSuggestedGender();
        final String line = stated
                ? FrameWording.wording(rules, target, "reminder.narrator")
                        .replace("{gender}", narrator.gender().name().toLowerCase(Locale.ROOT))
                : "";
        log.debug("Run frame reminders: narrator line {}", stated ? "stated" : "none");
        return new RunFrame.ReminderWords(
                FrameWording.wording(rules, target, "reminder.marks"),
                FrameWording.wording(rules, target, "reminder.dialogue"),
                line);
    }

    /** The {@code source} and {@code target} slots of the translate templates: the languages' English names. */
    static Map<String, String> languageSlots(@Nullable final String source, final String target) {
        return Map.of("source", nameOf(source), "target", FrameWording.englishName(target));
    }

    private static String nameOf(@Nullable final String tag) {
        return tag == null ? PromptLanguages.describe(null) : FrameWording.englishName(tag);
    }

    private String bullets(final List<RuleLine> lines) {
        return lines.stream()
                .map(line -> BULLET + words.get(BlockWords.LABEL + line.rule().key()) + ": " + line.text())
                .collect(Collectors.joining("\n"));
    }

    private static Block system(final String slot, final String text) {
        return new Block(slot, Block.Role.SYSTEM, Block.Stability.RUN, text);
    }

    private static String hashOf(final List<Block> blocks) {
        final String joined = blocks.stream()
                .map(block -> block.slot() + SLOT_END + block.text())
                .collect(Collectors.joining(BLOCK_SEPARATOR));
        return HashUtil.sha256Hex(joined.getBytes(StandardCharsets.UTF_8));
    }
}
