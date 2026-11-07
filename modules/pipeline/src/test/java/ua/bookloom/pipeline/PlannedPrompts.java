package ua.bookloom.pipeline;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.context.BookMap;
import ua.bookloom.pipeline.context.ContextBuilder;
import ua.bookloom.pipeline.context.Focus;
import ua.bookloom.pipeline.context.FocusItem;
import ua.bookloom.pipeline.context.KindExtra;
import ua.bookloom.pipeline.context.PromptPlan;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.context.Window;
import ua.bookloom.pipeline.index.IndexTestBooks;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.NameRules;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The translate-family prompts as a run sends them: planned by the context builder over an invented en→uk book, a
 * marked item in focus, so a catalogue-wide test reads the draft, the format re-ask and the repair it really sends
 * rather than rendering their files alone; and the analysis prompts as their services build them on the ANALYSIS frame.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlannedPrompts {

    /** The prompts the context builder plans; their files hold only part of what is sent. */
    public static final Set<PromptName> PLANNED =
            Set.of(PromptName.TRANSLATE, PromptName.FORMAT_REASK, PromptName.REPAIR, PromptName.PROOFREAD);

    /** The analysis prompts (design D8): planned on the ANALYSIS frame, their files holding the task alone. */
    public static final Set<PromptName> ANALYSIS = EnumSet.of(
            PromptName.TERM_CHOICE,
            PromptName.NAME_KNOWLEDGE,
            PromptName.FILE_NAME,
            PromptName.BRIEF_SUGGESTION,
            PromptName.BOOK_KNOWLEDGE,
            PromptName.BRIEF_SYNTHESIS,
            PromptName.TERM_READING,
            PromptName.LEXICON_READING,
            PromptName.TERM_SUMMARY,
            PromptName.PRESCAN,
            PromptName.REVIEW_TERMS,
            PromptName.SUGGEST_TARGETS,
            PromptName.CHAPTER_SUMMARY);

    /** The task slots the call frame fills; a caller gives the others. */
    public static final Set<String> FRAME_SLOTS = Set.of(
            "sourceLanguage",
            "targetLanguage",
            "styleSheet",
            "foreignPassageRule",
            "examples",
            "exampleReply",
            "languageRules");

    private static final String SEGMENT = "body:0";
    private static final ContextBuilder BUILDER = new ContextBuilder(new PromptTemplates());
    private static final OutputLimit LIMIT = new OutputLimit(100, 200);

    /**
     * The plan of one translate-family call over a masked text.
     *
     * @param name one of {@link #PLANNED}
     * @param masked the item's masked text
     * @return the plan; never null
     */
    public static PromptPlan plan(final PromptName name, final String masked) {
        final BookBrief brief = BookBrief.defaults("en").withLanguages("en", "uk");
        final TranslatePlanner planner =
                TestPlanners.planner(brief, IndexTestBooks.oneUnit(masked), ModelProfile.defaults());
        final CallKind kind = name.callKind();
        if (kind == CallKind.PROOFREAD) {
            return proofreadPlan(planner, brief, masked);
        }
        final Focus focus = new Focus(
                List.of(new FocusItem(SEGMENT, masked)),
                kind == CallKind.REPAIR
                        ? Map.of(SEGMENT, List.of("echo (BLOCK): the target repeats the source"))
                        : Map.of());
        final KindExtra extra =
                switch (name) {
                    case FORMAT_REASK -> KindExtra.reask("Here is the translation.", "was not valid JSON", "");
                    case REPAIR -> KindExtra.repair(masked, "");
                    default -> KindExtra.NONE;
                };
        return Objects.requireNonNull(
                        planner.plan(kind, TestPlanners.empty(planner), focus, extra)
                                .data(),
                        "plan")
                .plan();
    }

    // A proofread window of the one item, its current text the item itself, marked by an echo.
    private static PromptPlan proofreadPlan(
            final TranslatePlanner planner, final BookBrief brief, final String masked) {
        final Focus focus =
                new Focus(List.of(new FocusItem(SEGMENT, masked, masked)), Map.of(SEGMENT, List.of("flagged: echo")));
        return Objects.requireNonNull(
                        planner.planProofread(Window.of(focus), BookMap.of(List.of(), List.of(), brief))
                                .data(),
                        "plan")
                .plan();
    }

    /**
     * The two messages of an analysis call as its service sends them.
     *
     * @param name one of {@link #ANALYSIS}
     * @param frame the call frame
     * @param values the task's own slot values, none of {@link #FRAME_SLOTS}
     * @return the system and the user message
     */
    public static List<ChatMessage> analysisMessages(
            final PromptName name, final CallFrame frame, final Map<String, String> values) {
        return BUILDER.analysisRequest(name, frame, values, LIMIT).messages();
    }

    /**
     * The two messages of an analysis call with every own slot of its task filled with {@code filler}, the name rule
     * the transliterating policy's.
     *
     * @param name one of {@link #ANALYSIS}
     * @param frame the call frame
     * @param filler the value of every own slot
     * @return the system and the user message
     */
    public static List<ChatMessage> filledAnalysisMessages(
            final PromptName name, final CallFrame frame, final String filler) {
        final Map<String, String> values = new HashMap<>();
        name.userSlots().required().forEach(slot -> values.put(slot, filler));
        name.userSlots().optional().forEach(slot -> values.put(slot, filler));
        values.keySet().removeAll(FRAME_SLOTS);
        values.computeIfPresent(
                "nameRule",
                (slot, value) -> NameRules.bundled().rule(NamePolicy.TRANSLITERATE, frame.targetLanguage()));
        return analysisMessages(name, frame, values);
    }

    /**
     * One message of {@link #filledAnalysisMessages}.
     *
     * @param name one of {@link #ANALYSIS}
     * @param frame the call frame
     * @param filler the value of every own slot
     * @param role which message
     * @return its text
     */
    public static String filledAnalysisText(
            final PromptName name, final CallFrame frame, final String filler, final ChatRole role) {
        return filledAnalysisMessages(name, frame, filler).stream()
                .filter(message -> message.role() == role)
                .map(ChatMessage::content)
                .findFirst()
                .orElseThrow();
    }

    /** The analysis prompts, for a {@code @MethodSource}. */
    public static Stream<PromptName> analysisNames() {
        return ANALYSIS.stream();
    }

    /** The two messages of a planned call, as the request carries them. */
    public static List<ChatMessage> messages(final PromptName name, final String masked) {
        final PromptPlan plan = plan(name, masked);
        return List.of(
                new ChatMessage(ChatRole.SYSTEM, plan.systemText()), new ChatMessage(ChatRole.USER, plan.userText()));
    }
}
