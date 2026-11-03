package ua.bookloom.llm.verify;

import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.StageOutcome;

/**
 * What the inference stage learned: the verdict, the answer it read (null when none arrived), whether the structured
 * probe validated, the last request it sent, so the test dialog can show the wire body, and — when the structured probe
 * was not confirmed and the plain probe answered — whether the schema was refused or ignored, for the model's settings; and the reasoning field the probe was answered
 * under, which a hosted server's later calls send.
 */
record InferenceProbe(
        StageOutcome outcome,
        @Nullable ChatResponse response,
        boolean structured,
        ChatRequest sent,
        ProbeVerdicts.@Nullable Outcome schema,
        @Nullable String thinkingSwitch) {

    InferenceProbe(StageOutcome outcome, @Nullable ChatResponse response, boolean structured, ChatRequest sent) {
        this(outcome, response, structured, sent, null, null);
    }

    InferenceProbe(
            StageOutcome outcome,
            @Nullable ChatResponse response,
            boolean structured,
            ChatRequest sent,
            ProbeVerdicts.@Nullable Outcome schema) {
        this(outcome, response, structured, sent, schema, null);
    }

    InferenceProbe withThinking(@Nullable String switchName) {
        return new InferenceProbe(outcome, response, structured, sent, schema, switchName);
    }
}
