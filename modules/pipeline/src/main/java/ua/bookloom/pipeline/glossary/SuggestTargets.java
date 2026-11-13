package ua.bookloom.pipeline.glossary;

import static ua.bookloom.util.contract.ArgNames.ERROR;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.context.ContextBuilder;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Refusal;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Replies;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;
import ua.bookloom.pipeline.glossary.TermEvidence.Evidence;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.NameRules;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.util.lang.Script;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * The model's suggested target for each open glossary entry, the third step of the review and of the name scan, after
 * the verdicts: a call of its own, since a small model does one task per call far better than two. The rendering
 * follows the Book Brief's name policy — under Keep original a name or a brand keeps its source spelling without any
 * call, and only a common noun is asked about. Nothing is written here; the caller writes once every batch has answered.
 */
@Slf4j
public final class SuggestTargets {

    /** How many entries one call carries, small enough for a small model to answer every one. */
    static final int SUGGEST_BATCH = 20;

    private final PromptTemplates templates;
    private final ContextBuilder builder;
    private final ObjectMapper mapper;

    /**
     * Builds its requests on the ANALYSIS frame (design D8) from the catalogue's task templates.
     *
     * @param templates the non-null prompt catalogue
     * @param mapper the non-null object mapper
     */
    @Inject
    public SuggestTargets(final PromptTemplates templates, final ObjectMapper mapper) {
        this.templates = Objects.requireNonNull(templates, "templates");
        this.builder = new ContextBuilder(this.templates);
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * The entries with the targets the model suggests for them written in, for entries the glossary does not hold yet —
     * the name scan's confirmed proposals — so nothing is written here either.
     *
     * @param entries the entries to give a target; never null
     * @param story the book's body segments; never null
     * @param frame the run's language pair and style; never null
     * @param policy the Book Brief's name policy; never null
     * @param calls the seam every model call goes through; never null
     * @param context the held glossary and the entries' readings, which the names' clusters and flags come from;
     *     never null
     * @return the entries in their order, each with its suggested target where the model gave one; or the first failed
     *     call's error
     */
    public Result<List<GlossaryEntry>> suggestOnto(
            final List<GlossaryEntry> entries,
            final BookSlice story,
            final CallFrame frame,
            final NamePolicy policy,
            final ModelCalls calls,
            final TargetContext context) {
        return suggest(entries, story, frame, policy, calls, context).map(answered -> {
            final Map<String, Suggestion> byId = new HashMap<>();
            answered.forEach(suggestion -> byId.putIfAbsent(suggestion.entry().id(), suggestion));
            log.debug("Suggested targets for {} of {} entries", byId.size(), entries.size());
            return entries.stream()
                    .map(entry -> Optional.ofNullable(byId.get(entry.id()))
                            .map(suggestion -> suggestion.onto(entry))
                            .orElse(entry))
                    .toList();
        });
    }

    /**
     * The entries with the targets the model suggests for them written in, knowing nothing of the glossary beyond the
     * entries.
     *
     * @param entries the entries to give a target; never null
     * @param story the book's body segments; never null
     * @param frame the run's language pair and style; never null
     * @param policy the Book Brief's name policy; never null
     * @param calls the seam every model call goes through; never null
     * @return the entries in their order, each with its suggested target where the model gave one; or the first failed
     *     call's error
     */
    public Result<List<GlossaryEntry>> suggestOnto(
            final List<GlossaryEntry> entries,
            final BookSlice story,
            final CallFrame frame,
            final NamePolicy policy,
            final ModelCalls calls) {
        return suggestOnto(entries, story, frame, policy, calls, TargetContext.none());
    }

    /**
     * Suggests a target for each entry, with the glossary and the readings the entries belong to (15i.N6).
     *
     * @param entries the open entries, with the type and gender the verdicts gave them; never null
     * @param story the book's body segments, where each entry's example sentences are read; never null
     * @param frame the run's language pair and style; never null
     * @param policy the Book Brief's name policy; never null
     * @param calls the seam every model call goes through; never null
     * @param context the held glossary and the entries' readings; never null
     * @return the suggestions, at most one per entry and none for an entry the model left without one or answered
     *     twice with a refused rendering; or the first failed call's error
     */
    Result<List<Suggestion>> suggest(
            final List<GlossaryEntry> entries,
            final BookSlice story,
            final CallFrame frame,
            final NamePolicy policy,
            final ModelCalls calls,
            final TargetContext context) {
        return suggest(entries, story, frame, policy, calls, context, refused -> {});
    }

    /** As the six-argument {@code suggest}, telling each batch's count of renderings refused twice to {@code rejected}. */
    Result<List<Suggestion>> suggest(
            final List<GlossaryEntry> entries,
            final BookSlice story,
            final CallFrame frame,
            final NamePolicy policy,
            final ModelCalls calls,
            final TargetContext context,
            final IntConsumer rejected) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(calls, "calls");
        Objects.requireNonNull(context, "context");
        final TargetContext known = context.withCommonWords(CommonWords.of(entries, story, frame.analysisLanguage()));
        final List<Suggestion> kept = new ArrayList<>(SettledSuggestions.of(entries, frame, policy, known));
        final List<GlossaryEntry> asked = unsettled(entries, kept);
        final SuggestionGroups groups = SuggestionGroups.of(asked, policy, SUGGEST_BATCH);
        logStart(policy, entries.size(), kept.size(), groups);
        final Result<List<Suggestion>> answered =
                askAll(groups, new Setting(story, frame, policy, calls, known, rejected));
        if (answered.isErr()) {
            log.warn("Glossary suggestions failed; nothing is suggested");
            return answered;
        }
        kept.addAll(Objects.requireNonNull(answered.data(), "answered"));
        final List<Suggestion> locked = SuggestionLocks.apply(kept, frame, policy, known);
        logFinish(locked, entries.size());
        return Result.ok(locked);
    }

    private static List<GlossaryEntry> unsettled(final List<GlossaryEntry> entries, final List<Suggestion> settled) {
        final Set<String> ids =
                settled.stream().map(suggestion -> suggestion.entry().id()).collect(Collectors.toSet());
        return entries.stream().filter(entry -> !ids.contains(entry.id())).toList();
    }

    private static void logFinish(final List<Suggestion> locked, final int entries) {
        log.info(
                "Glossary suggestions finished suggested={} of {} meaningNotes={}",
                locked.size(),
                entries,
                locked.stream()
                        .filter(suggestion -> suggestion.meaning() != null)
                        .count());
    }

    private static void logStart(
            final NamePolicy policy, final int entries, final int kept, final SuggestionGroups groups) {
        log.info(
                "Glossary suggestions started policy={} entries={} kept={} translatedByChoice={} batches={}",
                policy,
                entries,
                kept,
                groups.translatedByChoice(policy),
                groups.batches());
    }

    // Each group under its own policy, so its task states that policy and shows that policy's example.
    private Result<List<Suggestion>> askAll(final SuggestionGroups groups, final Setting base) {
        final List<Suggestion> answered = new ArrayList<>();
        for (final Map.Entry<NamePolicy, List<GlossaryEntry>> group :
                groups.byPolicy().entrySet()) {
            final Setting setting = new Setting(
                    base.story(), base.frame(), group.getKey(), base.calls(), base.context(), base.rejected());
            final Result<List<Suggestion>> asked = ask(group.getValue(), setting, groups);
            if (asked.isErr()) {
                return asked;
            }
            answered.addAll(Objects.requireNonNull(asked.data(), "asked"));
        }
        return Result.ok(answered);
    }

    /**
     * Suggests a target for each entry, knowing nothing of the glossary beyond the entries.
     *
     * @param entries the open entries, with the type and gender the verdicts gave them; never null
     * @param story the book's body segments, where each entry's example sentences are read; never null
     * @param frame the run's language pair and style; never null
     * @param policy the Book Brief's name policy; never null
     * @param calls the seam every model call goes through; never null
     * @return the suggestions, at most one per entry and none for an entry the model left without one; or the first
     *     failed call's error
     */
    Result<List<Suggestion>> suggest(
            final List<GlossaryEntry> entries,
            final BookSlice story,
            final CallFrame frame,
            final NamePolicy policy,
            final ModelCalls calls) {
        return suggest(entries, story, frame, policy, calls, TargetContext.none());
    }

    /** What every call of one suggestion run shares. */
    private record Setting(
            BookSlice story,
            CallFrame frame,
            NamePolicy policy,
            ModelCalls calls,
            TargetContext context,
            IntConsumer rejected) {}

    /** What one run asks and checks with, built once for all its batches. */
    private record Run(
            Setting setting,
            Map<String, String> task,
            Map<String, Evidence> evidence,
            @Nullable Script refused,
            @Nullable Script required,
            TargetValidator checks,
            ClusterAgreement cluster) {}

    private Result<List<Suggestion>> ask(
            final List<GlossaryEntry> asked, final Setting setting, final SuggestionGroups counter) {
        if (asked.isEmpty()) {
            return Result.ok(List.of());
        }
        final Run run = runOf(asked, setting);
        final int batches = Math.ceilDiv(asked.size(), SUGGEST_BATCH);
        final List<Suggestion> suggestions = new ArrayList<>();
        for (int index = 0; index < batches; index++) {
            final List<GlossaryEntry> batch =
                    asked.subList(index * SUGGEST_BATCH, Math.min(asked.size(), (index + 1) * SUGGEST_BATCH));
            setting.calls().announce(counter.next());
            final Result<List<Suggestion>> answered = runBatch(index, batch, run);
            if (answered.isErr()) {
                return answered;
            }
            suggestions.addAll(Objects.requireNonNull(answered.data(), "suggestions"));
        }
        return Result.ok(run.cluster().align(SuggestionGroups.noted(suggestions, setting.policy(), setting.context())));
    }

    private Run runOf(final List<GlossaryEntry> asked, final Setting setting) {
        final CallFrame frame = setting.frame();
        // The style sheet is data the renderings are chosen against, so the task carries it, never the system part.
        final Map<String, String> task = Map.of(
                "nameRule",
                NameRules.bundled().rule(setting.policy(), frame.targetLanguage()),
                "examples",
                templates.nameExamples(frame, setting.policy()));
        final List<GlossaryEntry> glossary = setting.context().heldWith(asked);
        final NameClusters clusters = NameClusters.of(glossary, setting.story().index(), frame.analysisLanguage());
        return new Run(
                setting,
                task,
                evidenceOf(setting.story(), asked),
                SuggestionScripts.refused(frame, setting.policy()),
                SuggestionScripts.required(frame),
                new TargetValidator(frame, setting.policy(), setting.context()),
                ClusterAgreement.over(clusters, setting.context().held(), asked));
    }

    private static Map<String, Evidence> evidenceOf(final BookSlice story, final List<GlossaryEntry> asked) {
        return TermEvidence.of(
                story.index(),
                story.ordinals(),
                asked.stream().map(GlossaryEntry::term).toList());
    }

    // A batch is asked once; the renderings the letter table or the identity rule refuse are asked once more, each
    // with what was refused, and one refused again is left without a target.
    private Result<List<Suggestion>> runBatch(final int index, final List<GlossaryEntry> batch, final Run run) {
        final Result<Replies> first = exchange(index, batch, Map.of(), run);
        if (first.isErr()) {
            return Result.err(Objects.requireNonNull(first.error(), ERROR));
        }
        final Replies replies = Objects.requireNonNull(first.data(), "replies");
        final List<Suggestion> kept = new ArrayList<>(replies.kept());
        final Map<String, Refusal> refused = replies.refusedNotAnswered();
        if (refused.isEmpty()) {
            return Result.ok(kept);
        }
        log.info("Suggestion batch {}: {} renderings refused, asked once more", index, refused.size());
        final List<GlossaryEntry> again =
                refused.values().stream().map(Refusal::entry).toList();
        final Result<Replies> second = exchange(index, again, refused, run);
        if (second.isErr()) {
            return Result.err(Objects.requireNonNull(second.error(), ERROR));
        }
        final List<Suggestion> retried = RefusedAgain.settle(refused, Objects.requireNonNull(second.data(), "replies"));
        log.info("Suggestion batch {}: {} of {} refused renderings replaced", index, retried.size(), refused.size());
        run.setting().rejected().accept(Math.max(0, refused.size() - retried.size()));
        RefusedAgain.warnLeftWithoutTarget(index, refused, retried);
        kept.addAll(retried);
        return Result.ok(kept);
    }

    private Result<Replies> exchange(
            final int index, final List<GlossaryEntry> batch, final Map<String, Refusal> refused, final Run run) {
        final ChatRequest request = requestFor(index, batch, refused, run);
        final Result<ChatResponse> reply = run.setting()
                .calls()
                .callAbout(CallKind.SUGGEST_TARGETS, List.of(), request, SuggestionReplies.asking(batch));
        if (reply.isErr()) {
            final AppError error = Objects.requireNonNull(reply.error(), ERROR);
            log.warn(
                    "Glossary suggestion batch {} of size {} failed code={} reason={}",
                    index,
                    batch.size(),
                    error.code(),
                    FailureReason.tokenIn(error.details()));
            return Result.err(error);
        }
        final String content = Objects.requireNonNull(reply.data(), "reply").content();
        log.trace("Glossary suggestion batch {} reply {}", index, content);
        final Replies read = readReply(content, batch, run);
        run.setting().calls().answered(SuggestionReplies.shown(batch, read.kept()));
        log.debug(
                "Glossary suggestion batch {} answered: size={} suggestions={} refused={}",
                index,
                batch.size(),
                read.kept().size(),
                read.refused().size());
        return Result.ok(read);
    }

    // Each target is stored without the stress marks its language asks to remove before any rule reads it.
    private Replies readReply(final String content, final List<GlossaryEntry> batch, final Run run) {
        final Map<String, GlossaryEntry> byKey = new LinkedHashMap<>();
        batch.forEach(entry -> byKey.put(GlossaryKeys.of(entry.term()), entry));
        final String target = run.setting().frame().targetLanguage();
        return SuggestionReplies.read(
                mapper,
                content,
                byKey,
                run.refused(),
                run.required(),
                run.checks(),
                text -> Objects.requireNonNull(TargetText.clean(text, target), "clean"));
    }

    private ChatRequest requestFor(
            final int index, final List<GlossaryEntry> batch, final Map<String, Refusal> refused, final Run run) {
        final Setting setting = run.setting();
        final String lines = batch.stream()
                .map(entry -> SuggestionLines.of(
                        entry,
                        run.evidence().getOrDefault(entry.term(), Evidence.NONE),
                        setting.context().readings().get(entry.id()),
                        run.cluster().statementFor(entry),
                        refused.get(entry.id()),
                        setting.context().commonWordsOf(entry)))
                .collect(Collectors.joining("\n"));
        final Map<String, String> values = new HashMap<>(run.task());
        values.put("terms", lines);
        final ChatRequest request = builder.analysisRequest(
                PromptName.SUGGEST_TARGETS, setting.frame(), values, OutputLimit.forSuggestions(batch.size()));
        log.trace("Glossary suggestion batch {} messages {}", index, request.messages());
        return request;
    }
}
