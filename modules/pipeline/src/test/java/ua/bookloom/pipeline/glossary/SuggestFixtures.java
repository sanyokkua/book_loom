package ua.bookloom.pipeline.glossary;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.ModelCalls;

/** What the name-suggestion tests share: entries, a scripted call seam and replies in the schema's shape. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SuggestFixtures {

    static GlossaryEntry entry(
            final String term, final TermType type, final Gender gender, @Nullable final String target) {
        return new GlossaryEntry(
                "p1:" + term.toLowerCase(Locale.ROOT).replace(' ', '-'), "p1", term, target, type, gender, false);
    }

    static ModelCalls calls(final ScriptedChatModel model) {
        return new ModelCalls() {
            @Override
            public Result<ChatResponse> call(
                    final CallKind kind, @Nullable final String segmentId, final ChatRequest request) {
                return model.chat(request);
            }

            @Override
            public void announce(final JobEvent event) {
                // no test of the suggestion text looks at the announcements
            }
        };
    }

    static Result<ChatResponse> says(final String... termAndTargetPairs) {
        final String items = IntStream.range(0, termAndTargetPairs.length / 2)
                .mapToObj(i -> "{\"term\":\"" + termAndTargetPairs[2 * i] + "\",\"target\":\""
                        + termAndTargetPairs[2 * i + 1] + "\"}")
                .collect(Collectors.joining(","));
        return reply("{\"suggestions\":[" + items + "]}");
    }

    static Result<ChatResponse> reply(final String content) {
        return Result.ok(new ChatResponse(Objects.requireNonNull(content), FinishReason.STOP));
    }
}
