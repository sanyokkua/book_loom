package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Loads the real-run corpus (15e.2) from {@code eval/realrun/}: synthetic text only, written to reproduce the shapes of
 * the defects the 6 h 17 min run of a 3,700-segment book showed, never copied from a book.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RunCorpus {

    static final String SOURCE_LANGUAGE = PromptEvalCases.SOURCE_LANGUAGE;
    static final String TARGET_LANGUAGE = PromptEvalCases.TARGET_LANGUAGE;

    private static final String DIRECTORY = "/eval/realrun/";

    /** Quote, script, narrator and short-line cases: one source with a clean and a defective candidate. */
    static List<RunCase> text() {
        return load("text.json", new TypeReference<>() {});
    }

    /** Batch drafts with scripted replies. */
    static List<BatchCase> batches() {
        return load("batch.json", new TypeReference<>() {});
    }

    /** Reviewer calls over several pairs. */
    static List<ReviewerBatchCase> reviewerBatches() {
        return load("reviewer.json", new TypeReference<>() {});
    }

    /** Placeholder and structural repair calls. */
    static List<RepairCase> repairs() {
        return load("repair.json", new TypeReference<>() {});
    }

    /** Deterministic scans of the key-term and name scans. */
    static List<ScanCase> scans() {
        return load("scan.json", new TypeReference<>() {});
    }

    private static <T> List<T> load(final String file, final TypeReference<List<T>> type) {
        try (InputStream in = RunCorpus.class.getResourceAsStream(DIRECTORY + file)) {
            Objects.requireNonNull(in, file);
            return new ObjectMapper().readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
