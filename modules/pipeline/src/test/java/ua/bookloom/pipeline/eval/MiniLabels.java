package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The case labels of a dataset ({@code eval/datasets/<id>/labels.json}), one per block. A report breaks results down
 * by label value, and a value with fewer than {@value #MIN_CASES} cases says "not interpretable" instead of a rate.
 *
 * @param byBlock the label of each block by block id, in reading order
 */
record MiniLabels(Map<String, Label> byBlock) {

    static final MiniLabels NONE = new MiniLabels(Map.of());

    static final int MIN_CASES = 8;

    /**
     * What kind of case a block is.
     *
     * @param task what is asked of the model (heading, dialogue, prose, name, code, injection)
     * @param pair the language pair
     * @param difficulty easy, medium or hard, as the case author judged it
     * @param length the source's word count class: {@code <=10}, {@code 11-30} or {@code >30}
     * @param defectClass the defect class the block exists for, {@code none} for plain prose
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Label(String task, String pair, String difficulty, String length, String defectClass) {}

    /** Keeps the reading order. */
    MiniLabels {
        byBlock = Collections.unmodifiableMap(new LinkedHashMap<>(byBlock));
    }

    static MiniLabels load(final String dataset) {
        final String resource = "/eval/datasets/" + dataset + "/labels.json";
        try (InputStream in = MiniLabels.class.getResourceAsStream(resource)) {
            return new MiniLabels(JsonMapper.builder()
                    .build()
                    .readValue(
                            Objects.requireNonNull(in, resource),
                            new TypeReference<LinkedHashMap<String, Label>>() {}));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * One line per label value: how many cases it has and how many were usable, or "not interpretable" below
     * {@value #MIN_CASES} cases.
     *
     * @param usableByBlock whether each block ended usable
     * @return the lines, empty when there are no labels
     */
    String describe(final Map<String, Boolean> usableByBlock) {
        final StringBuilder out = new StringBuilder();
        section(out, "task", Label::task, usableByBlock);
        section(out, "pair", Label::pair, usableByBlock);
        section(out, "difficulty", Label::difficulty, usableByBlock);
        section(out, "length", Label::length, usableByBlock);
        section(out, "defectClass", Label::defectClass, usableByBlock);
        return out.toString();
    }

    private void section(
            final StringBuilder out,
            final String field,
            final Function<Label, String> value,
            final Map<String, Boolean> usableByBlock) {
        final Map<String, Long> cases = byBlock.values().stream()
                .collect(Collectors.groupingBy(value, LinkedHashMap::new, Collectors.counting()));
        cases.forEach((v, n) -> {
            final long usable = byBlock.entrySet().stream()
                    .filter(e -> value.apply(e.getValue()).equals(v))
                    .filter(e -> usableByBlock.getOrDefault(e.getKey(), false))
                    .count();
            out.append("  LABEL %s=%s: %d cases, ".formatted(field, v, n))
                    .append(n < MIN_CASES ? "not interpretable" : usable + " usable")
                    .append('\n');
        });
    }
}
