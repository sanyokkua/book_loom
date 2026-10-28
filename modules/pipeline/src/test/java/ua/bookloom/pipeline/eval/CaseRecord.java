package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.pipeline.eval.MiniExpectations.CheckKind.Level;

/**
 * One case of one repeat, as a line of {@code cases.jsonl}: enough to reproduce that single result (the seed) and to
 * read it again (its graders, time, tokens and the calls file {@link #replyRef} names). A figure the provider did not
 * report is {@code null}, never {@code 0}, so a missing measurement can never read as a fast or free one.
 *
 * @param caseId the case's id, such as a block id {@code ch02.md:14}
 * @param repeat the 0-based repeat
 * @param seed the seed the repeat's requests carried
 * @param graders each grader's verdict on the case
 * @param time the case's call times
 * @param tokens the case's token counts
 */
record CaseRecord(String caseId, int repeat, int seed, List<Grader> graders, Time time, Tokens tokens) {

    static final String CALLS_DIR = "calls";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Rejects a missing part and copies the graders. */
    CaseRecord {
        Objects.requireNonNull(caseId, "caseId");
        graders = List.copyOf(graders);
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(tokens, "tokens");
    }

    /**
     * One grader's verdict.
     *
     * @param id the grader's name, such as {@code NUMBERS_KEPT}
     * @param level how hard its failure counts
     * @param passed whether the case met it
     */
    record Grader(String id, Level level, boolean passed) {

        /** Rejects a missing part. */
        Grader {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(level, "level");
        }
    }

    /**
     * The case's call times in milliseconds, each summed over its calls; null when any call did not report it.
     *
     * @param total the prompt reading and the generation together, or null when either is missing
     * @param promptRead the time the server spent reading the prompts, or null
     * @param ttft the time to the first token, or null: the chat seam does not report it
     * @param generation the time spent generating the replies, or null
     */
    record Time(
            @Nullable Long total,
            @Nullable Long promptRead,
            @Nullable Long ttft,
            @Nullable Long generation) {

        /** The times of a case's calls, from the usage each reply reported. */
        static Time of(final List<StageProbe.Call> calls) {
            final Long promptRead = sum(calls, usage -> millis(usage.promptEval()));
            final Long generation = sum(calls, usage -> millis(usage.generation()));
            final Long total = promptRead == null || generation == null ? null : promptRead + generation;
            return new Time(total, promptRead, null, generation);
        }

        private static @Nullable Long millis(@Nullable final Duration duration) {
            return duration == null ? null : duration.toMillis();
        }
    }

    /**
     * The case's token counts, each summed over its calls; null when any call did not report it.
     *
     * @param prompt the prompt tokens, or null
     * @param completion the completion tokens, or null
     * @param cached the prompt tokens the server took from its cache, or null
     */
    record Tokens(
            @Nullable Long prompt,
            @Nullable Long completion,
            @Nullable Long cached) {

        /** The counts of a case's calls. */
        static Tokens of(final List<StageProbe.Call> calls) {
            return new Tokens(
                    sum(calls, usage -> count(usage.prompt())),
                    sum(calls, usage -> count(usage.completion())),
                    sum(calls, usage -> count(usage.cachedPrompt())));
        }

        private static @Nullable Long count(@Nullable final Integer value) {
            return value == null ? null : value.longValue();
        }
    }

    /**
     * One call of the case, as {@code calls/<case>-<k>.json} keeps it.
     *
     * @param prompt the messages sent, each line starting with its role
     * @param reply the reply's text, or null when the call failed
     */
    record Exchange(String prompt, @Nullable String reply) {

        /** Rejects a missing prompt. */
        Exchange {
            Objects.requireNonNull(prompt, "prompt");
        }

        /** A probed call as its prompt and reply. */
        static Exchange of(final StageProbe.Call call) {
            final String prompt = call.request().messages().stream()
                    .map(message -> "[" + message.role().name().toLowerCase(Locale.ROOT) + "] " + message.content())
                    .collect(Collectors.joining("\n"));
            return new Exchange(prompt, call.failed() ? null : call.reply());
        }
    }

    /**
     * A case's record with the calls its calls file keeps.
     *
     * @param record the record, written as a line of {@code cases.jsonl}
     * @param calls the case's calls in the order they were sent
     */
    record Entry(CaseRecord record, List<Exchange> calls) {

        /** Rejects a missing part and copies the calls. */
        Entry {
            Objects.requireNonNull(record, "record");
            calls = List.copyOf(calls);
        }
    }

    /** The calls file of this case and repeat, relative to the run folder; a {@code :} in the id becomes {@code _}. */
    String replyRef() {
        return CALLS_DIR + "/" + caseId.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + repeat + ".json";
    }

    /** The record as one JSON line. */
    String json() {
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("caseId", caseId);
        fields.put("repeat", repeat);
        fields.put("seed", seed);
        fields.put("graders", graders);
        fields.put("time", time);
        fields.put("tokens", tokens);
        fields.put("replyRef", replyRef());
        try {
            return MAPPER.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The sum of a figure over the calls; null when a call did not report it, or there are no calls. */
    private static @Nullable Long sum(
            final List<StageProbe.Call> calls, final Function<TokenUsage, @Nullable Long> figure) {
        if (calls.isEmpty()) {
            return null;
        }
        long total = 0;
        for (final StageProbe.Call call : calls) {
            final TokenUsage usage = call.usage();
            final Long value = usage == null ? null : figure.apply(usage);
            if (value == null) {
                return null;
            }
            total += value;
        }
        return total;
    }
}
