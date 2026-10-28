package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a reviewer run on the corpus must reach, by model class (one file, {@code eval/thresholds.json}).
 *
 * @param falseNegativeMax the share of defective candidates the reviewer may leave alone (one minus the catch rate)
 * @param falsePositiveMax the share of clean candidates the reviewer may ask to change
 * @param stabilityMin the share of cases whose repeated runs must agree
 */
record EvalThresholds(double falseNegativeMax, double falsePositiveMax, double stabilityMin) {

    private static final Pattern SIZE = Pattern.compile("[:/_-](\\d+(?:\\.\\d+)?)b(?![a-z0-9])");
    private static final double LARGE_FROM = 20;
    private static final double MID_FROM = 10;

    /**
     * The model class of a model id, read from the size the id states: e2b, small (under 10b, or no size stated), mid
     * (10b up to 20b) or large (20b and more).
     */
    static String classOf(final String model) {
        final String id = model.toLowerCase(Locale.ROOT);
        if (id.contains("e2b")) {
            return "e2b";
        }
        final Matcher size = SIZE.matcher(id);
        final double billions = size.find() ? Double.parseDouble(size.group(1)) : 0;
        if (billions >= LARGE_FROM) {
            return "large";
        }
        return billions >= MID_FROM ? "mid" : "small";
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
