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

/** Loads the labelled regression corpus of synthetic defect cases from the test resources. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EvalCorpus {

    static final String DEFECTS = "/eval/defects.json";

    static List<DefectCase> defects() {
        return load(DEFECTS);
    }

    static List<DefectCase> load(final String resource) {
        try (InputStream in = EvalCorpus.class.getResourceAsStream(resource)) {
            Objects.requireNonNull(in, resource);
            return new ObjectMapper().readValue(in, new TypeReference<List<DefectCase>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
