package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.eval.ProbeCase.Sending;

/**
 * Every case the probe knows, each wired to the eval class that sends it in the suites: the prompt cases and the
 * defects corpus through {@link PromptEvalRunner}, the realrun files through {@link RealRunRunner}. Nothing here builds
 * a request itself.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProbeCases {

    static final String PROMPT = "prompt";
    static final String DEFECTS = "defects";
    static final String REALRUN = "realrun";
    private static final int CLOSEST = 5;

    static List<ProbeCase> all() {
        final List<ProbeCase> cases = new ArrayList<>();
        PromptEvalCases.ALL.forEach(c -> cases.add(new ProbeCase(c.name(), PROMPT, s -> prompt(c, s))));
        EvalCorpus.defects().forEach(c -> cases.add(new ProbeCase(c.id(), DEFECTS, s -> defect(c, s))));
        RunCorpus.text()
                .forEach(c -> cases.add(realRun(c.id(), s -> rows(runner(s).text(c)))));
        RunCorpus.batches()
                .forEach(c ->
                        cases.add(realRun(c.id(), s -> rows(List.of(runner(s).batch(c))))));
        RunCorpus.reviewerBatches()
                .forEach(c -> cases.add(realRun(c.id(), s -> rows(runner(s).reviewerBatch(c)))));
        RunCorpus.repairs()
                .forEach(c ->
                        cases.add(realRun(c.id(), s -> rows(List.of(runner(s).repair(c))))));
        RunCorpus.injections()
                .forEach(c ->
                        cases.add(realRun(c.id(), s -> rows(List.of(runner(s).injection(c))))));
        return List.copyOf(cases);
    }

    /** The case with this id; a prompt case wins over a defects or realrun case of the same id. */
    static Optional<ProbeCase> find(final String id) {
        log.debug("Probe case lookup id={}", id);
        return all().stream().filter(c -> c.id().equals(id)).findFirst();
    }

    /** Up to five ids nearest to {@code id}: those that contain it or that it contains first, then by edit distance. */
    static List<String> closest(final String id) {
        final String wanted = id.toLowerCase(Locale.ROOT);
        return all().stream()
                .map(ProbeCase::id)
                .distinct()
                .sorted(Comparator.comparingInt((String other) -> distance(wanted, other.toLowerCase(Locale.ROOT))))
                .limit(CLOSEST)
                .toList();
    }

    private static ProbeCase realRun(final String id, final java.util.function.Function<Sending, List<String>> run) {
        return new ProbeCase(id, REALRUN, run);
    }

    private static RealRunRunner runner(final Sending sending) {
        return new RealRunRunner(sending.calls(), 1, sending.workDir());
    }

    private static List<String> prompt(final EvalCase evalCase, final Sending sending) {
        return new PromptEvalRunner(
                        sending.calls(),
                        PromptEvalCases.SOURCE_LANGUAGE,
                        PromptEvalCases.TARGET_LANGUAGE,
                        sending.window())
                .runAll(List.of(evalCase)).stream().map(ProbeCases::describe).toList();
    }

    private static List<String> defect(final DefectCase defect, final Sending sending) {
        return new PromptEvalRunner(
                        sending.calls(),
                        PromptEvalCases.SOURCE_LANGUAGE,
                        PromptEvalCases.TARGET_LANGUAGE,
                        sending.window())
                .runDefects(List.of(defect), 1).stream()
                        .map(row -> "defective=" + row.defective() + " flagged=" + row.flagged() + " readable="
                                + row.readable() + " reviewerFlagged=" + row.reviewerFlagged() + " tokenBreaks="
                                + row.tokenBreaks())
                        .toList();
    }

    private static List<String> rows(final List<RealRunRow> rows) {
        return rows.stream()
                .map(row -> "outcome=" + row.outcome().label() + " call=" + row.call() + " kind=" + row.kind()
                        + " stable=" + row.stable() + " truncated=" + row.truncated() + " | " + row.id() + ": "
                        + row.detail())
                .toList();
    }

    private static String describe(final EvalRow row) {
        return "parsed=" + row.parsed().label() + " gate=" + row.gate().label() + " script="
                + row.script().label() + " marker=" + row.marker().label() + " injection="
                + row.injection().label() + " reviewed=" + row.reviewed().label() + " | " + row.detail();
    }

    private static int distance(final String a, final String b) {
        if (b.contains(a) || a.contains(b)) {
            return Math.abs(a.length() - b.length());
        }
        final int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            final int[] current = new int[b.length() + 1];
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                final int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            System.arraycopy(current, 0, previous, 0, current.length);
        }
        return previous[b.length()] + Math.max(a.length(), b.length());
    }
}
