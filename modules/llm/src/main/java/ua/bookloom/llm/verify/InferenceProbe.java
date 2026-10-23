package ua.bookloom.llm.verify;

import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.StageOutcome;

/**
 * What the inference stage learned: the verdict, the answer it read (null when none arrived), whether the structured
 * probe validated, and the last request it sent, so the test dialog can show the wire body.
 */
record InferenceProbe(StageOutcome outcome, @Nullable ChatResponse response, boolean structured, ChatRequest sent) {}
