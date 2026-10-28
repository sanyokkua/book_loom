package ua.bookloom.pipeline.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.pipeline.eval.JudgeReply.Preferred;
import ua.bookloom.pipeline.eval.JudgeReply.Winner;
import ua.bookloom.pipeline.eval.JudgeRubric.Mode;
import ua.bookloom.util.hash.HashUtil;

/**
 * The local judge (design D24): asks a model, through the production {@code :llm} chat model a live run passes in (the
 * inference gate, the reply scrubber), how a translation reads. A comparison is asked twice with the order swapped and
 * counts as a win only when both orders name the same translation, so a judge that prefers whatever stands first
 * yields a tie, never a win. Replies are cached by judge, quantisation, server, rubric hash, mode, thinking and input,
 * so a second run of the same comparison costs no call and gives the same answer.
 */
@Slf4j
final class JudgeRunner {

    static final String CACHE_DIR = ".temporary_context/eval-history/judge-cache";
    private static final double TEMPERATURE = 0.0;
    static final int SEED = 1;
    private static final int MAX_REPLY_TOKENS = 1024;
    private static final int EXPECTED_REPLY_TOKENS = 400;

    private final ChatModel model;
    private final Setup setup;
    private final JudgeRubric rubric;
    private final Path cacheDir;

    /**
     * The judge model as it is installed.
     *
     * @param model the judge model's id, such as {@code gemma4:26b}
     * @param quant its quantisation, or null when not stated
     * @param server the serving program and its version, such as {@code ollama 0.12.6}, or null when not stated
     * @param reasoning the explicit thinking setting sent with every request
     */
    record Setup(
            String model, @Nullable String quant, @Nullable String server, ReasoningLevel reasoning) {

        /** Rejects a missing part. */
        Setup {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(reasoning, "reasoning");
        }

        /** A judge with thinking off, the default. */
        static Setup of(final String model, @Nullable final String quant, @Nullable final String server) {
            return new Setup(model, quant, server, ReasoningLevel.OFF);
        }

        String reasoningWord() {
            return reasoning.name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The report's {@code judge} block.
     *
     * @param model the judge model's id
     * @param quant its quantisation, or null
     * @param server the serving program and version, or null
     * @param rubricVersion the rubric's id
     * @param rubricHash the rubric file's hash
     * @param reasoning the thinking setting, {@code off}, {@code low} or {@code on}
     * @param mode how the judge was asked
     * @param sameFamily whether the translator is a model of the judge's family, whose judgement may favour it
     * @param calibration the calibration set and its agreement, or null before the judge is calibrated
     */
    record Block(
            String model,
            @Nullable String quant,
            @Nullable String server,
            String rubricVersion,
            String rubricHash,
            String reasoning,
            String mode,
            boolean sameFamily,
            @Nullable String calibration) {

        /** The block in its JSON order. */
        Map<String, Object> json() {
            final Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("model", model);
            fields.put("quant", quant);
            fields.put("server", server);
            fields.put("rubricVersion", rubricVersion);
            fields.put("rubricHash", rubricHash);
            fields.put("reasoning", reasoning);
            fields.put("mode", mode);
            fields.put("sameFamily", sameFamily);
            fields.put("calibration", calibration);
            return fields;
        }
    }

    /** One comparison of a batch: {@code first} and {@code second} in the caller's order. */
    record PairCase(JudgeItem item, String first, String second) {

        /** Rejects a missing part. */
        PairCase {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
        }
    }

    /** A comparison's outcome in the caller's order. */
    enum PairVerdict {
        /** Both orders preferred the first translation. */
        FIRST,
        /** Both orders preferred the second translation. */
        SECOND,
        /** The orders disagreed or both said tie. */
        TIE,
        /** The two outputs are the same text; nothing was asked. */
        IDENTICAL,
        /** A reply could not be read or the call failed; no judgement. */
        UNPARSED
    }

    private record Asked(@Nullable String reply, boolean cached) {}

    /**
     * The two answers of one comparison in the caller's names: A is the caller's first translation, B its second.
     *
     * @param forward the answer with the first shown as A, or null when it could not be read
     * @param swapped the answer with the order swapped, turned back into the caller's names, or null likewise
     */
    record PairOrders(@Nullable Winner forward, @Nullable Winner swapped) {}

    private record Judged(PairVerdict verdict, PairOrders orders, int sent, int cached) {}

    JudgeRunner(final ChatModel model, final Setup setup, final JudgeRubric rubric, final Path cacheDir) {
        this.model = Objects.requireNonNull(model, "model");
        this.setup = Objects.requireNonNull(setup, "setup");
        this.rubric = Objects.requireNonNull(rubric, "rubric");
        this.cacheDir = Objects.requireNonNull(cacheDir, "cacheDir");
    }

    /** The cache under the repository's eval history. */
    static Path defaultCacheDir() {
        return EvalProvenance.repoRoot().resolve(CACHE_DIR);
    }

    /** The report's judge block for a run whose translator was {@code translatorModel}. */
    Block block(final String translatorModel) {
        Objects.requireNonNull(translatorModel, "translatorModel");
        return new Block(
                setup.model(),
                setup.quant(),
                setup.server(),
                JudgeRubric.VERSION,
                rubric.hash(),
                setup.reasoningWord(),
                Mode.PAIRWISE.wire(),
                family(setup.model()).equals(family(translatorModel)),
                null);
    }

    private static String family(final String modelId) {
        final int colon = modelId.indexOf(':');
        return (colon < 0 ? modelId : modelId.substring(0, colon)).strip().toLowerCase(Locale.ROOT);
    }

    /** The comparisons of a batch in order; only cases whose two outputs differ are asked. */
    List<PairVerdict> judgePairs(final List<PairCase> cases) {
        Objects.requireNonNull(cases, "cases");
        final List<Judged> judged =
                cases.stream().map(c -> judged(c.item(), c.first(), c.second())).toList();
        log.info(
                "Judge batch judge={} cases={} identical={} judged={} fromCache={}",
                setup.model(),
                cases.size(),
                judged.stream()
                        .filter(j -> j.verdict() == PairVerdict.IDENTICAL)
                        .count(),
                judged.stream().mapToInt(Judged::sent).sum(),
                judged.stream().mapToInt(Judged::cached).sum());
        return judged.stream().map(Judged::verdict).toList();
    }

    /** One comparison: a win only when both orders agree, else a tie. */
    PairVerdict judgePair(final JudgeItem item, final String first, final String second) {
        return judged(item, first, second).verdict();
    }

    /** One translation's judgement. */
    JudgeReply judgeOne(final JudgeItem item, final String translation) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(translation, "translation");
        log.debug("Judge pointwise case={} translationLength={}", item.caseId(), translation.length());
        final Asked asked = ask(Mode.POINTWISE, item.pointwise(translation));
        final JudgeReply reply = asked.reply() == null
                ? new JudgeReply.Unparsed("the call failed")
                : JudgeReplyParser.parsePointwise(asked.reply(), translation);
        log.info("Judge pointwise case={} fromCache={} result={}", item.caseId(), asked.cached(), reply.describe());
        return reply;
    }

    /**
     * Both orders' answers of one comparison, in the caller's names: {@code forward} as asked with {@code first} shown
     * as A, {@code swapped} as asked with the order swapped and turned back into the caller's names. Two identical
     * outputs are not asked and are a tie both ways.
     *
     * @return the two answers; an answer is null when its reply could not be read or its call failed
     */
    PairOrders judgePairOrders(final JudgeItem item, final String first, final String second) {
        return judged(item, first, second).orders();
    }

    private Judged judged(final JudgeItem item, final String first, final String second) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first.equals(second)) {
            log.debug("Judge pair case={} outputs identical, not asked", item.caseId());
            return new Judged(PairVerdict.IDENTICAL, new PairOrders(Winner.TIE, Winner.TIE), 0, 0);
        }
        final Asked forward = ask(Mode.PAIRWISE, item.pairwise(first, second));
        final Asked swapped = ask(Mode.PAIRWISE, item.pairwise(second, first));
        final PairOrders orders = new PairOrders(winner(forward), unswapped(winner(swapped)));
        final PairVerdict verdict = verdict(orders);
        log.debug(
                "Judge pair case={} verdict={} forward={} swapped={} forwardCached={} swappedCached={}",
                item.caseId(),
                verdict,
                orders.forward(),
                orders.swapped(),
                forward.cached(),
                swapped.cached());
        final int cached = (forward.cached() ? 1 : 0) + (swapped.cached() ? 1 : 0);
        return new Judged(verdict, orders, 2 - cached, cached);
    }

    private static @Nullable Winner winner(final Asked asked) {
        return asked.reply() != null && JudgeReplyParser.parsePairwise(asked.reply()) instanceof Preferred preferred
                ? preferred.winner()
                : null;
    }

    // In the swapped order the first translation stands as B, so its A means the caller's second and its B the first.
    private static @Nullable Winner unswapped(@Nullable final Winner swapped) {
        return swapped == null
                ? null
                : switch (swapped) {
                    case A -> Winner.B;
                    case B -> Winner.A;
                    case TIE -> Winner.TIE;
                };
    }

    private static PairVerdict verdict(final PairOrders orders) {
        if (orders.forward() == null || orders.swapped() == null) {
            return PairVerdict.UNPARSED;
        }
        if (orders.forward() != orders.swapped() || orders.forward() == Winner.TIE) {
            return PairVerdict.TIE;
        }
        return orders.forward() == Winner.A ? PairVerdict.FIRST : PairVerdict.SECOND;
    }

    private Asked ask(final Mode mode, final String user) {
        final Path cached = cacheDir.resolve(cacheKey(mode, user) + ".txt");
        log.trace("Judge input mode={} text={}", mode.wire(), user);
        if (Files.isRegularFile(cached)) {
            log.debug("Judge reply served from the cache mode={} file={}", mode.wire(), cached.getFileName());
            return new Asked(read(cached), true);
        }
        final Result<ChatResponse> response = model.chat(request(mode, user));
        if (response.isErr()) {
            log.warn(
                    "Judge call failed mode={} code={}",
                    mode.wire(),
                    Objects.requireNonNull(response.error()).code());
            return new Asked(null, false);
        }
        final String reply = Objects.requireNonNull(response.data()).content();
        log.trace("Judge reply mode={} text={}", mode.wire(), reply);
        write(cached, reply);
        return new Asked(reply, false);
    }

    private ChatRequest request(final Mode mode, final String user) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.SYSTEM, rubric.system(mode)), new ChatMessage(ChatRole.USER, user)),
                TEMPERATURE,
                null,
                setup.reasoning(),
                null,
                EXPECTED_REPLY_TOKENS,
                MAX_REPLY_TOKENS,
                SEED,
                null);
    }

    // Thinking is in the key too: the same judge with thinking on answers differently.
    private String cacheKey(final Mode mode, final String user) {
        final String input = HashUtil.sha256Hex(user.getBytes(StandardCharsets.UTF_8));
        final String key = String.join(
                "\n",
                setup.model(),
                Objects.requireNonNullElse(setup.quant(), ""),
                Objects.requireNonNullElse(setup.server(), ""),
                rubric.hash(),
                mode.wire(),
                setup.reasoningWord(),
                input);
        return HashUtil.sha256Hex(key.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(final Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(final Path file, final String reply) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, reply, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
