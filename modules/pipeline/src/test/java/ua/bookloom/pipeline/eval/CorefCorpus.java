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

/** Loads the invented coreference windows, {@code eval/coref/windows.json}. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CorefCorpus {

    static final String WINDOWS = "/eval/coref/windows.json";

    /** The windows of the fixture, in file order. */
    static List<CorefWindow> windows() {
        try (InputStream in = CorefCorpus.class.getResourceAsStream(WINDOWS)) {
            Objects.requireNonNull(in, WINDOWS);
            return new ObjectMapper().readValue(in, new TypeReference<List<CorefWindow>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
