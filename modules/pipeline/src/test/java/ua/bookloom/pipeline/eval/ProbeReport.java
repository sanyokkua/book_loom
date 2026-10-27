package ua.bookloom.pipeline.eval;

import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.TokenUsage;

/** Prints one probe repeat as plain text: each call's request, reply and usage, then the case's verdict. */
final class ProbeReport {

    private static final String NOT_REPORTED = "-";

    private final PrintStream out;

    ProbeReport(final PrintStream out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    void repeat(
            final int number,
            final int of,
            final List<ProbeModel.Exchange> exchanges,
            final List<String> verdict,
            final boolean dry) {
        out.println();
        out.println("=== repeat " + number + "/" + of + " ===");
        if (exchanges.isEmpty()) {
            out.println("(no model call: the production code decided this case without one)");
        }
        for (int i = 0; i < exchanges.size(); i++) {
            call(i + 1, exchanges.get(i));
        }
        out.println("[verdict]" + (dry ? " (dry: no call made, nothing scored)" : ""));
        if (!dry) {
            verdict.forEach(line -> out.println("  " + line));
        }
    }

    private void call(final int number, final ProbeModel.Exchange exchange) {
        final ChatRequest request = exchange.request();
        out.println("-- call " + number + " (" + request.callKind() + ") --");
        out.println("[request] " + parameters(request));
        for (final ChatMessage message : request.messages()) {
            message(message);
        }
        final Result<ChatResponse> reply = exchange.reply();
        if (reply == null) {
            out.println("[reply] (dry: no call made)");
            return;
        }
        reply(reply);
        out.println(
                "[usage] " + usage(reply.data() == null ? null : reply.data().usage(), exchange.wallMs()));
    }

    private void message(final ChatMessage message) {
        final String role = message.role().name().toLowerCase(java.util.Locale.ROOT);
        out.println("[" + role + "] (" + message.content().length() + " chars)");
        final List<String> sections = sections(message.content());
        for (int i = 0; i < sections.size(); i++) {
            final String section = sections.get(i);
            out.println("  -- section " + (i + 1) + " (" + (int) section.lines().count() + " lines)");
            section.lines().forEach(line -> out.println("     " + line));
        }
    }

    private void reply(final Result<ChatResponse> reply) {
        final AppError error = reply.error();
        if (error != null) {
            out.println("[reply] FAILED " + error.code() + ": " + error.message());
            return;
        }
        final ChatResponse response = Objects.requireNonNull(reply.data());
        out.println("[reply] finish=" + response.finishReason());
        out.println(response.content());
    }

    /** The request's knobs as the provider receives them. */
    static String parameters(final ChatRequest request) {
        final var format = request.responseFormat();
        return "num_ctx=" + request.contextWindow() + " maxOutput=" + request.maxOutputTokens() + " expectedOutput="
                + request.expectedOutputTokens() + " temperature=" + request.temperature() + " seed=" + request.seed()
                + " format="
                + (format == null
                        ? "none"
                        : format.name() + " (schema " + format.jsonSchema().length() + " chars)")
                + " think=" + think(request.reasoningEnabled()) + " sampling=" + request.sampling();
    }

    private static String think(@Nullable final ReasoningLevel reasoning) {
        if (reasoning == null) {
            return "provider-default";
        }
        return reasoning.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The usage the provider reported, with the wall time the probe measured. */
    static String usage(@Nullable final TokenUsage usage, final long wallMs) {
        if (usage == null) {
            return "prompt=- completion=- promptEvalMs=- generationMs=- cachedPrompt=- wallMs=" + wallMs;
        }
        return "prompt=" + shown(usage.prompt()) + " completion=" + shown(usage.completion()) + " promptEvalMs="
                + millis(usage.promptEval()) + " generationMs=" + millis(usage.generation()) + " cachedPrompt="
                + shown(usage.cachedPrompt()) + " wallMs=" + wallMs;
    }

    private static String shown(@Nullable final Integer value) {
        return value == null ? NOT_REPORTED : value.toString();
    }

    private static String millis(@Nullable final Duration duration) {
        return duration == null ? NOT_REPORTED : Long.toString(duration.toMillis());
    }

    /** A message cut into the blocks the template separates with a blank line. */
    static List<String> sections(final String content) {
        final List<String> sections = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        for (final String line : content.split("\n", -1)) {
            if (line.isBlank()) {
                flush(sections, current);
            } else {
                current.append(current.isEmpty() ? "" : "\n").append(line);
            }
        }
        flush(sections, current);
        return sections;
    }

    private static void flush(final List<String> sections, final StringBuilder current) {
        if (!current.isEmpty()) {
            sections.add(current.toString());
            current.setLength(0);
        }
    }
}
