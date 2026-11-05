package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelWindow;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.prompt.PromptLanguages;

/**
 * The fixed part of every analysis call (design D8, blocks 1, 2 and 7): the role naming the book's language, the
 * sentence that book text is data, and the sentence asking for JSON only. A call that reads the book to understand it
 * does not translate, so it carries no style sheet, no rules and no examples; a brief or names call made before any run
 * builds it from the source language alone.
 *
 * @param blocks the three system blocks, in the order sent; copied
 */
public record AnalysisFrame(List<Block> blocks) {

    private static final String SOURCE = "{source}";
    private static final String INSTRUCTIONS_LANGUAGE = "en";

    /** Copies the blocks. */
    public AnalysisFrame {
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
    }

    /**
     * The frame of a book written in {@code sourceLanguage}.
     *
     * @param sourceLanguage the source language tag the person chose, or null under Auto, when the role asks the model
     *     to infer it from the text
     * @return the frame; never null
     */
    public static AnalysisFrame of(@Nullable final String sourceLanguage) {
        final BlockWords words = BlockWords.bundled();
        return new AnalysisFrame(List.of(
                system(
                        BlockSlots.ROLE,
                        words.get(BlockWords.ANALYSIS_ROLE).replace(SOURCE, PromptLanguages.describe(sourceLanguage))),
                system(BlockSlots.DATA, words.get(BlockWords.ANALYSIS_DATA)),
                system(BlockSlots.REPLY, words.get(BlockWords.ANALYSIS_REPLY))));
    }

    /**
     * The plan of a call on this frame: its three system blocks, then the call's user block.
     *
     * @param task the call's one user block, the task with the book's text it reads
     * @param cap the most tokens the reply may take; positive
     * @return the plan, sized for the default window, which the model-call seam replaces by the model's (task 6.9)
     */
    PromptPlan plan(final Block task, final int cap) {
        final List<Block> all = new ArrayList<>(blocks);
        all.add(task);
        // The fixed sentences are English; a task holds English instructions around the book's text: the mixed rate.
        final int tokens = TokenEstimator.estimateCall(
                all.stream()
                        .map(block -> new TokenEstimator.Message(
                                block.text(), block.role() == Block.Role.SYSTEM ? INSTRUCTIONS_LANGUAGE : null))
                        .toList(),
                ModelProfile.defaults().templateOverhead());
        return new PromptPlan(all, tokens, cap, ModelWindow.DEFAULT_WINDOW);
    }

    private static Block system(final String slot, final String text) {
        return new Block(slot, Block.Role.SYSTEM, Block.Stability.RUN, text);
    }
}
