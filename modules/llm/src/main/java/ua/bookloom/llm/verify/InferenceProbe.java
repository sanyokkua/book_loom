package ua.bookloom.llm.verify;

import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.StageOutcome;

/**
 * What the inference stage learned: the verdict, the answer it read (null when none arrived), whether the structured
 * probe validated, the last request it sent, so the test dialog can show the wire body, and — when the structured probe
 * was not confirmed and the plain probe answered — whether the schema was refused or ignored, for the model's settings.
 */
record InferenceProbe(
        StageOutcome outcome,
        @Nullable ChatResponse response,
        boolean structured,
        ChatRequest sent,
        ProbeVerdicts.@Nullable Outcome schema) {

    InferenceProbe(StageOutcome outcome, @Nullable ChatResponse response, boolean structured, ChatRequest sent) {
        this(outcome, response, structured, sent, null);
    }
}
