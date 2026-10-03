package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * What a judge run on the corpus must reach, by model class (one file, {@code eval/thresholds.json}).
 *
 * @param falseNegativeMax the share of defective candidates the judge may accept
 * @param falsePositiveMax the share of clean candidates the judge may refuse
 * @param stabilityMin the share of cases whose repeated runs must agree
 */
record EvalThresholds(double falseNegativeMax, double falsePositiveMax, double stabilityMin) {

    /** The model class of a model id: e2b, small (4b class), mid (12b class) or large. */
    static String classOf(final String model) {
        final String id = model.toLowerCase(Locale.ROOT);
        if (id.contains("e2b")) {
            return "e2b";
        }
        if (id.matches(".*(2[0-9]|30)b.*")) {
            return "large";
        }
        return id.contains("12b") ? "mid" : "small";
    }

    static EvalThresholds forModel(final String model) {
        try (InputStream in = EvalThresholds.class.getResourceAsStream("/eval/thresholds.json")) {
            Objects.requireNonNull(in, "thresholds.json");
            final Map<String, EvalThresholds> all =
                    new ObjectMapper().readValue(in, new TypeReference<Map<String, EvalThresholds>>() {});
            return all.get(classOf(model));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
