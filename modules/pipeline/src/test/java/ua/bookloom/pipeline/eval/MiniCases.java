package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.eval.CaseRecord.Entry;
import ua.bookloom.pipeline.eval.CaseRecord.Exchange;
import ua.bookloom.pipeline.eval.CaseRecord.Grader;
import ua.bookloom.pipeline.eval.CaseRecord.Time;
import ua.bookloom.pipeline.eval.CaseRecord.Tokens;
import ua.bookloom.pipeline.eval.MiniExpectations.CheckKind.Level;

/**
 * The per-case records of one mini run: one case per block with the expectations that concern it, and one
 * {@value #GLOSSARY_CASE} case for the glossary expectations. A block's calls are the calls whose prompt holds its
 * text, so a batch shared by several blocks is counted for each of them, and a case no call mentions reports no
 * figures rather than zeros.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MiniCases {

    static final String GLOSSARY_CASE = "glossary";

    /**
     * The cases of a run.
     *
     * @param run the non-null run the results scored
     * @param results the non-null scored expectations
     * @param repeat the 0-based repeat
     * @param seed the seed the repeat's requests carried
     * @return the cases in block order, the glossary case last
     */
    static List<Entry> of(final MiniRun run, final List<MiniResult> results, final int repeat, final int seed) {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(results, "results");
        log.debug(
                "Mini cases blocks={} results={} repeat={} seed={}",
                run.outcomes().size(),
                results.size(),
                repeat,
                seed);
        final List<Entry> cases = new ArrayList<>();
        for (final MiniRun.Outcome outcome : run.outcomes().values()) {
            final List<Grader> graders = results.stream()
                    .filter(r -> r.blocks().contains(outcome.id()))
                    .map(r -> new Grader(r.id(), r.level(), !r.failingBlocks().contains(outcome.id())))
                    .toList();
            cases.add(entry(outcome.id(), repeat, seed, graders, callsOf(run, outcome.source())));
        }
        final List<Grader> glossary = results.stream()
                .filter(r -> r.blocks().isEmpty())
                .map(r -> new Grader(r.id(), Level.SOFT, r.passed()))
                .toList();
        cases.add(entry(GLOSSARY_CASE, repeat, seed, glossary, List.of()));
        return List.copyOf(cases);
    }

    private static Entry entry(
            final String id,
            final int repeat,
            final int seed,
            final List<Grader> graders,
            final List<StageProbe.Call> calls) {
        final CaseRecord record = new CaseRecord(id, repeat, seed, graders, Time.of(calls), Tokens.of(calls));
        return new Entry(record, calls.stream().map(Exchange::of).toList());
    }

    private static List<StageProbe.Call> callsOf(final MiniRun run, final String source) {
        return run.sent().stream()
                .filter(call -> call.request().messages().stream()
                        .anyMatch(m -> m.content().contains(source)))
                .toList();
    }
}
