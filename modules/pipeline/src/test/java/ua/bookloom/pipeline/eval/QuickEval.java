package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The quick subsets of the prompt evals (15j.J16): a handful of cases that still touch every defect class, so a prompt
 * change is read in minutes before any whole suite is paid for. {@code BOOKLOOM_EVAL_QUICK=1} (set by
 * {@code scripts/eval-matrix.sh --quick}) selects them; a realrun case joins its subset with {@code "quick": true} in
 * its JSON file.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class QuickEval {

    static final String SWITCH = "BOOKLOOM_EVAL_QUICK";

    /**
     * One case per defect class of {@link PromptEvalCases} and a clean control ({@code plain-short}): a locked name,
     * balanced quotes, narrator gender, a drop-cap token, a copied number, an instruction inside the book text, a
     * glossary rendering, a directed fix and a reviewer pair.
     */
    static final List<String> PROMPT_CASES = List.of(
            "dialogue-locked-name",
            "quotes-balanced",
            "gender-she",
            "drop-cap",
            "number-only",
            "injection-hello",
            "glossary-name",
            "fix-echo",
            "review-omission",
            "plain-short");

    /** Whether the environment asks for the quick subsets. */
    static boolean isOn(final Map<String, String> env) {
        final String value = env.get(SWITCH);
        final boolean on =
                value != null && (value.strip().equals("1") || value.strip().equalsIgnoreCase("true"));
        log.debug("Quick eval switch {}={} on={}", SWITCH, value, on);
        return on;
    }

    /** The ids of every realrun case marked quick, over all the case files. */
    static List<String> realRunQuickIds() {
        return Stream.of(
                        RunCorpus.text().stream().filter(RunCase::isQuick).map(RunCase::id),
                        RunCorpus.batches().stream().filter(BatchCase::isQuick).map(BatchCase::id),
                        RunCorpus.reviewerBatches().stream()
                                .filter(ReviewerBatchCase::isQuick)
                                .map(ReviewerBatchCase::id),
                        RunCorpus.repairs().stream().filter(RepairCase::isQuick).map(RepairCase::id),
                        RunCorpus.injections().stream()
                                .filter(InjectionCase::isQuick)
                                .map(InjectionCase::id))
                .flatMap(ids -> ids)
                .toList();
    }
}
