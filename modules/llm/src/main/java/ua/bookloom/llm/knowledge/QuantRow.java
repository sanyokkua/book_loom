package ua.bookloom.llm.knowledge;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import ua.bookloom.api.llm.QuantTier;
import ua.bookloom.api.llm.SizeClass;

/**
 * One base quant type of {@code quant-tiers.csv}.
 *
 * @param token the upper-case base type, such as "Q4_K_M"
 * @param family float, legacy, k, i, ternary or mx
 * @param bpw nominal bits per weight
 * @param needsImatrix yes, recommended, no or n/a
 * @param tiers the tier per size class; a class the file leaves empty is absent
 * @param confidence high, medium or low
 * @param source a short text naming where the row comes from
 */
public record QuantRow(
        String token,
        String family,
        double bpw,
        String needsImatrix,
        Map<SizeClass, QuantTier> tiers,
        String confidence,
        String source) {

    public QuantRow {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(needsImatrix, "needsImatrix");
        Objects.requireNonNull(tiers, "tiers");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(source, "source");
        tiers = tiers.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(tiers));
    }

    /**
     * Looks up the tier for a size class.
     *
     * @param sizeClass the band to judge in
     * @return the tier, or empty when the file leaves the cell empty
     */
    public Optional<QuantTier> tier(SizeClass sizeClass) {
        return Optional.ofNullable(tiers.get(sizeClass));
    }
}
