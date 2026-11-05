package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.prompt.ModelCalls;

/** The draft step built as a run builds it, and the outcomes it answers with, for the tests of the step alone. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DraftStepFixtures {

    static SegmentTranslator segmentTranslator(
            final DocumentPort documents,
            final ChatModel model,
            final BookFormat format,
            final String targetLanguage,
            @Nullable final String sourceLanguage) {
        return segmentTranslator(
                GateFunction.of(documents, format),
                (kind, segmentId, request) -> model.chat(request),
                format,
                targetLanguage,
                sourceLanguage);
    }

    static SegmentTranslator segmentTranslator(
            final GateFunction gate,
            final ModelCalls calls,
            final BookFormat format,
            final String targetLanguage,
            @Nullable final String sourceLanguage) {
        return new SegmentTranslator(
                gate, calls, format, TestPlanners.planner(sourceLanguage, targetLanguage), new ObjectMapper());
    }

    /** The draft a draft step answered with, failing the test when it answered anything else. */
    static DraftOutcome.Drafted drafted(final Result<DraftOutcome> result) {
        final DraftOutcome outcome = Objects.requireNonNull(result.data(), () -> "draft outcome: " + result.error());
        if (outcome instanceof DraftOutcome.Drafted drafted) {
            return drafted;
        }
        throw new AssertionError("expected a draft, got " + outcome);
    }

    /** The reason a draft step flagged its segment at once, failing the test when it answered anything else. */
    static AppError flaggedAtOnce(final Result<DraftOutcome> result) {
        final DraftOutcome outcome = Objects.requireNonNull(result.data(), () -> "draft outcome: " + result.error());
        if (outcome instanceof DraftOutcome.FlaggedAtOnce flagged) {
            return flagged.error();
        }
        throw new AssertionError("expected a segment flagged at once, got " + outcome);
    }
}
