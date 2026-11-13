package ua.bookloom.llm.verify;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.llm.client.MessageShaper;

/** The requests the inference stage sends: one asks for structured output, the other for the same answer as plain text. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProbeRequests {

    /** The probe's output cap: the smallest cap a run's call has, ample for {@code {"status":"ok"}}. */
    static final int OUTPUT_CAP = 64;

    static final String MESSAGE = "Return exactly one JSON object: {\"status\":\"ok\"}.";

    private static final ResponseFormat FORMAT = new ResponseFormat("verification_probe", """
            {"type":"object","properties":{"status":{"const":"ok"}},"required":["status"],"additionalProperties":false}
            """);

    static ChatRequest structured(@Nullable ReasoningLevel reasoningEnabled) {
        return build(FORMAT, reasoningEnabled);
    }

    static ChatRequest plain(@Nullable ReasoningLevel reasoningEnabled) {
        return build(null, reasoningEnabled);
    }

    // BookLoom's own system text goes first, as on every call, so a persona baked into the download is not what is
    // tested; ProbeShaping folds it into the user message for a template with no system role. Capped like a run's
    // calls: an uncapped probe let a thinking model reason to the end and answer, hiding a
    // server
    // that ignores the reasoning control and then spends every capped call of the run on reasoning.
    private static ChatRequest build(@Nullable ResponseFormat format, @Nullable ReasoningLevel reasoningEnabled) {
        return new ChatRequest(
                List.of(
                        new ChatMessage(ChatRole.SYSTEM, MessageShaper.DEFAULT_SYSTEM),
                        new ChatMessage(ChatRole.USER, MESSAGE)),
                null,
                format,
                reasoningEnabled,
                null,
                null,
                OUTPUT_CAP,
                null,
                null);
    }
}
