package ua.bookloom.pipeline;

import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftStep;

/**
 * How a draft step's requests are made and its replies read. A run plans them through the context builder as a batch
 * of one ({@link TranslateDraftPrompts}), and so does a review retry.
 */
interface DraftPrompts {

    /** The source language tag, or null when it is inferred from the text. */
    @Nullable
    String sourceLanguage();

    /** The target language tag. */
    String targetLanguage();

    /**
     * The request of one step of a draft.
     *
     * @param attempt the non-null draft
     * @param step the non-null step
     * @param rejected the rejected reply a re-ask quotes or the previous attempt a repair rewrites; empty for a draft
     * @param diagnostic the reader's diagnosis or the repair's defect lines; empty for a draft
     * @return the request, before the run's seam sizes it
     */
    ChatRequest build(DraftAttempt attempt, DraftStep step, String rejected, String diagnostic);

    /** What the shown call of a step names besides its request, built from the same values. */
    CallDescriptor descriptor(DraftAttempt attempt, DraftStep step, String rejected, String diagnostic);

    /**
     * Reads a draft or re-ask reply.
     *
     * @param reply the non-null raw reply
     * @param attempt the non-null draft it answers
     * @return the translation, or the diagnostic of a reply outside the contract
     */
    ParsedReply parse(String reply, DraftAttempt attempt);

    /** Reads a repair reply, as {@link #parse} does with the repair's tolerance of edge control codes. */
    ParsedReply parseRepair(String reply, DraftAttempt attempt);

    /**
     * Whether a translation copies one of the examples the call showed.
     *
     * @param maskedTarget the non-null translation
     * @param attempt the non-null draft it answers
     * @return {@code true} if it copies one, {@code false} otherwise or when the prompts show none
     */
    boolean copiesExample(String maskedTarget, DraftAttempt attempt);
}
