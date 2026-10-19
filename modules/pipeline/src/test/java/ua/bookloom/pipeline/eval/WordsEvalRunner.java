package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.checks.CheckFinding;
import ua.bookloom.pipeline.checks.ModelWordValidator;

/** Sends the garbled-word corpus through a {@link ModelWordValidator} in batches and records what it flagged. */
@Slf4j
final class WordsEvalRunner {

    private final ModelWordValidator validator;
    private final int batchSize;

    WordsEvalRunner(final ModelWordValidator validator, final int batchSize) {
        this.validator = Objects.requireNonNull(validator, "validator");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive: " + batchSize);
        }
        this.batchSize = batchSize;
    }

    List<WordsEvalRow> run(final List<WordCase> cases) {
        final List<WordsEvalRow> rows = new ArrayList<>();
        for (int from = 0; from < cases.size(); from += batchSize) {
            final List<WordCase> batch = cases.subList(from, Math.min(cases.size(), from + batchSize));
            log.info("Words eval batch from={} size={}", from, batch.size());
            final List<List<CheckFinding>> found =
                    validator.findAll(batch.stream().map(WordCase::candidate).toList());
            for (int i = 0; i < batch.size(); i++) {
                final WordCase wordCase = batch.get(i);
                rows.add(new WordsEvalRow(
                        wordCase.id(),
                        wordCase.defective(),
                        wordCase.words(),
                        found.get(i).stream()
                                .map(finding -> finding.span().text())
                                .toList()));
            }
        }
        return rows;
    }
}
