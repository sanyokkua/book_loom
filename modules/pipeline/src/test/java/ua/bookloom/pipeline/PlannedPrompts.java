package ua.bookloom.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.pipeline.context.Focus;
import ua.bookloom.pipeline.context.FocusItem;
import ua.bookloom.pipeline.context.KindExtra;
import ua.bookloom.pipeline.context.PromptPlan;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.index.IndexTestBooks;
import ua.bookloom.pipeline.prompt.PromptName;

/**
 * The translate-family prompts as a run sends them: planned by the context builder over an invented en→uk book, a
 * marked item in focus, so a catalogue-wide test reads the draft, the format re-ask and the repair it really sends
 * rather than rendering their files alone.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlannedPrompts {

    /** The prompts the context builder plans; their files hold only part of what is sent. */
    public static final Set<PromptName> PLANNED =
            Set.of(PromptName.TRANSLATE, PromptName.FORMAT_REASK, PromptName.REPAIR);

    private static final String SEGMENT = "body:0";

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

    /** The two messages of a planned call, as the request carries them. */
    public static List<ChatMessage> messages(final PromptName name, final String masked) {
        final PromptPlan plan = plan(name, masked);
        return List.of(
                new ChatMessage(ChatRole.SYSTEM, plan.systemText()), new ChatMessage(ChatRole.USER, plan.userText()));
    }
}
