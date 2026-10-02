package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.eval.EvalCase.Expect;
import ua.bookloom.pipeline.eval.EvalCase.Suggest;
import ua.bookloom.pipeline.eval.EvalCase.SuggestedName;
import ua.bookloom.pipeline.eval.EvalRow.Check;
import ua.bookloom.pipeline.glossary.SuggestTargets;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * Sends a {@link Suggest} case through the production suggestion step and measures each name's target: one row per
 * name — parsed when the model gave it a target the step kept, script when the target is Cyrillic, marker when it
 * matches the dictionary form the case expects.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SuggestEval {

    private static final SuggestTargets SUGGEST = new SuggestTargets(new PromptTemplates(), new ObjectMapper());

    static List<EvalRow> rows(final Suggest suggest, final CallFrame frame, final ModelCalls calls) {
        final List<GlossaryEntry> entries = suggest.names().stream()
                .map(name -> new GlossaryEntry(
                        "eval:" + name.term(), "eval", name.term(), null, name.type(), Gender.UNKNOWN, false))
                .toList();
        final List<Segment> book =
                suggest.sentences().stream().map(PromptEvalRunner::segment).toList();
        final Result<List<GlossaryEntry>> answered = SUGGEST.suggestOnto(entries, book, frame, suggest.policy(), calls);
        final Map<String, GlossaryEntry> byTerm = answered.isOk()
                ? Objects.requireNonNull(answered.data()).stream()
                        .collect(Collectors.toMap(GlossaryEntry::term, Function.identity()))
                : Map.of();
        return suggest.names().stream()
                .map(name -> row(suggest.name(), name, byTerm.get(name.term())))
                .toList();
    }

    private static EvalRow row(final String caseName, final SuggestedName name, @Nullable final GlossaryEntry entry) {
        final String rowName = caseName + ":" + name.term();
        if (entry == null || !entry.isSuggested()) {
            return new EvalRow(
                    rowName, "suggest", Check.FAIL, Check.NA, Check.FAIL, Check.FAIL, Check.NA, Check.NA, "no target");
        }
        final String target = Objects.requireNonNull(entry.target());
        return new EvalRow(
                rowName,
                "suggest",
                Check.PASS,
                Check.NA,
                ReplyChecks.script(target, target, Expect.translate()),
                Check.of(Pattern.compile(name.marker()).matcher(target).find()),
                Check.NA,
                Check.NA,
                target + " (" + entry.gender().name().toLowerCase(Locale.ROOT) + ")");
    }
}
