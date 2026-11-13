package ua.bookloom.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.batch.BatchReply;
import ua.bookloom.pipeline.batch.BatchReplyParser;
import ua.bookloom.pipeline.batch.ItemOutcome;
import ua.bookloom.pipeline.batch.ItemStatus;
import ua.bookloom.pipeline.context.ContextBuilder;
import ua.bookloom.pipeline.context.EchoGate;
import ua.bookloom.pipeline.context.Focus;
import ua.bookloom.pipeline.context.FocusItem;
import ua.bookloom.pipeline.context.KindExtra;
import ua.bookloom.pipeline.context.PromptPlan;
import ua.bookloom.pipeline.context.TranslateContext;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ReplyKind;
import ua.bookloom.pipeline.prompt.DraftStep;
import ua.bookloom.pipeline.prompt.JsonReplies;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;

/**
 * A run's draft prompts (design D8, D11): a single draft is a batch of one item planned by the context builder, its
 * format re-ask and its one repair the same plan with block 11 added, and every reply is read as the one JSON object
 * {@code {"items":[{"id":"1","target":…}]}}: prose around it, a bare {@code {"target"}} or any other shape is no reply,
 * which earns the format re-ask. So a draft, its re-ask and its repair send the run frame's system bytes, the same
 * window and the same map lines.
 */
@Slf4j
final class TranslateDraftPrompts implements DraftPrompts {

    /** The id of a batch of one's only item. */
    static final String ONE = "1";

    static final String UNREADABLE = "was not one JSON object {\"items\":[{\"id\":\"1\",\"target\":\"…\"}]}";
    static final String NOT_JSON = "was not valid JSON";
    private static final String ITEMS = "items";
    // A model that closes the object and then its imagined wrapper leaves only quotes, braces and brackets behind it.
    private static final Pattern TRAILING_CLOSERS = Pattern.compile("[\\s\\]}\"]*");
    static final String NO_TARGET = "held no nonblank target for item 1";
    static final String TWICE = "answered item 1 more than once";

    private final TranslatePlanner planner;
    private final ObjectMapper mapper;
    private final BatchReplyParser parser;
    // The call's request and its shown parts come from one plan: the last one is kept so the parts are not planned
    // again. Used from the job thread only.
    private @Nullable Planned last;

    /** What a plan was planned for. */
    private record Key(DraftAttempt attempt, DraftStep step, String rejected, String diagnostic) {}

    /** A plan with what it was planned for. */
    private record Planned(Key key, PromptPlan plan) {}

    TranslateDraftPrompts(final TranslatePlanner planner, final ObjectMapper mapper) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.parser = new BatchReplyParser(mapper);
    }

    @Override
    public @Nullable String sourceLanguage() {
        return planner.frame().brief().sourceLanguage();
    }

    @Override
    public String targetLanguage() {
        return Objects.requireNonNull(planner.frame().brief().targetLanguage(), "targetLanguage");
    }

    @Override
    public ChatRequest build(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        final PromptPlan plan = plan(attempt, step, rejected, diagnostic);
        final OutputLimit limit =
                OutputLimit.forBatch(List.of(attempt.shownText()), sourceLanguage(), targetLanguage());
        final boolean lower = step == DraftStep.DRAFT && attempt.lowerTemperature();
        log.debug(
                "Translate request segmentId={} step={} promptTokens={} reserve={}",
                attempt.segment().id(),
                step,
                plan.promptTokens(),
                plan.maxOutputTokens());
        return ChatRequests.build(promptName(step), plan, limit, lower);
    }

    @Override
    public CallDescriptor descriptor(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        return CallDescriptor.of(
                promptName(step),
                DisplayText.of(attempt.segment().masked()),
                () -> plan(attempt, step, rejected, diagnostic).sections());
    }

    @Override
    public ParsedReply parse(final String reply, final DraftAttempt attempt) {
        final Optional<String> envelope = envelopeOf(reply);
        if (envelope.isEmpty()) {
            log.debug(
                    "Batch-of-one reply segmentId={} is no JSON object",
                    attempt.segment().id());
            return invalid(NOT_JSON);
        }
        final BatchReply read = parser.parse(
                envelope.get(), List.of(new BatchItem(ONE, attempt.shownText())), sourceLanguage(), targetLanguage());
        final Optional<ItemOutcome> item = read.readable() ? read.outcome(ONE) : Optional.empty();
        final ParsedReply parsed =
                item.map(found -> parsedOf(found, attempt.shownText())).orElseGet(() -> invalid(UNREADABLE));
        log.debug(
                "Batch-of-one reply segmentId={} readable={} kind={}",
                attempt.segment().id(),
                read.readable(),
                parsed.kind());
        return parsed;
    }

    // A repair's control code the reader could not place at the very start or end is dropped, as a single repair's
    // reader does; one inside the text still makes the reply malformed, so a damaged middle is never hidden.
    @Override
    public ParsedReply parseRepair(final String reply, final DraftAttempt attempt) {
        final ParsedReply parsed = parse(reply, attempt);
        if (!DraftReplyParser.CONTROL_CHARACTERS_DIAGNOSTIC.equals(parsed.diagnostic())) {
            return parsed;
        }
        final Optional<String> envelope = envelopeOf(reply);
        final BatchReply read = parser.parse(
                envelope.orElse(reply),
                List.of(new BatchItem(ONE, attempt.shownText())),
                sourceLanguage(),
                targetLanguage());
        final String trimmed = read.outcome(ONE)
                .map(ItemOutcome::target)
                .map(TranslateDraftPrompts::withoutEdgeCodes)
                .orElse("");
        log.debug(
                "Repair reply segmentId={} edge control codes dropped",
                attempt.segment().id());
        return trimmed.isBlank() || ControlCharacters.addsControl(attempt.shownText(), trimmed)
                ? parsed
                : new ParsedReply(ReplyKind.STRUCTURED, trimmed, "");
    }

    private static String withoutEdgeCodes(final String text) {
        int start = 0;
        int end = text.length();
        while (start < end && ControlCharacters.isControl(text.charAt(start))) {
            start++;
        }
        while (end > start && ControlCharacters.isControl(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    @Override
    public boolean copiesExample(final String maskedTarget, final DraftAttempt attempt) {
        return EchoGate.copies(
                maskedTarget, attempt.shownText(), planner.frame().examples());
    }

    // The whole reply is the one object holding an items array: prose before it or a bare target is no reply.
    private Optional<String> envelopeOf(final String reply) {
        final String text = reply.strip();
        if (!text.startsWith("{")) {
            return Optional.empty();
        }
        final Optional<JsonNode> whole = tree(text);
        if (whole.isPresent()) {
            return whole.get().path(ITEMS).isArray() ? Optional.of(text) : Optional.empty();
        }
        final OptionalInt end = JsonReplies.firstObjectEnd(text);
        if (end.isEmpty()
                || !TRAILING_CLOSERS.matcher(text.substring(end.getAsInt())).matches()) {
            return Optional.empty();
        }
        final String object = text.substring(0, end.getAsInt());
        return tree(object).filter(node -> node.path(ITEMS).isArray()).map(node -> object);
    }

    private Optional<JsonNode> tree(final String text) {
        try {
            return Optional.of(mapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(text));
        } catch (JsonProcessingException _) {
            return Optional.empty();
        }
    }

    // A control code the reader could not place is no translation; anything else is the gate's and the checks' to
    // judge.
    private static ParsedReply parsedOf(final ItemOutcome item, final String shown) {
        if (item.status() == ItemStatus.DUPLICATE) {
            return invalid(TWICE);
        }
        if (item.status() == ItemStatus.MISSING || item.target().isBlank()) {
            return invalid(NO_TARGET);
        }
        if (ControlCharacters.addsControl(shown, item.target())) {
            return invalid(DraftReplyParser.CONTROL_CHARACTERS_DIAGNOSTIC);
        }
        return new ParsedReply(ReplyKind.STRUCTURED, item.target(), "");
    }

    private static ParsedReply invalid(final String diagnostic) {
        return new ParsedReply(ReplyKind.INVALID_STRUCTURED, "", diagnostic);
    }

    private PromptPlan plan(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        final Key key = new Key(attempt, step, rejected, diagnostic);
        final @Nullable Planned held = last;
        if (held != null && held.key().equals(key)) {
            return held.plan();
        }
        final PromptPlan plan = planAnew(attempt, step, rejected, diagnostic);
        last = new Planned(key, plan);
        return plan;
    }

    private PromptPlan planAnew(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        final String id = attempt.segment().id();
        final TranslateContext context = Objects.requireNonNullElseGet(
                attempt.translate(), () -> planner.bare(id, ProtectedMask.none(attempt.shownText())));
        final Map<String, List<String>> reasons =
                step == DraftStep.REPAIR ? Map.of(id, diagnostic.lines().toList()) : Map.of();
        final Focus focus = new Focus(List.of(new FocusItem(id, attempt.shownText())), reasons);
        final var planned = planner.plan(step.callKind(), context, focus, extraOf(attempt, step, rejected, diagnostic));
        final ContextBuilder.FittedPlan fitted = planned.data();
        if (fitted == null) {
            final AppError error = Objects.requireNonNull(planned.error(), "error");
            throw new IllegalStateException("The draft of " + id + " could not be planned: " + error.code());
        }
        return fitted.plan();
    }

    private static KindExtra extraOf(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        return switch (step) {
            case DRAFT -> KindExtra.note(attempt.extraInstruction());
            case STRUCTURAL_REPAIR -> KindExtra.reask(rejected, diagnostic, attempt.extraInstruction());
            case REPAIR -> KindExtra.repair(rejected, attempt.extraInstruction());
        };
    }

    static PromptName promptName(final DraftStep step) {
        return switch (step) {
            case DRAFT -> PromptName.TRANSLATE;
            case STRUCTURAL_REPAIR -> PromptName.FORMAT_REASK;
            case REPAIR -> PromptName.REPAIR;
        };
    }
}
