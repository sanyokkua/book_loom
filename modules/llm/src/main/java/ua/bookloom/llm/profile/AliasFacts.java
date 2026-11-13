package ua.bookloom.llm.profile;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Facts a name does not state but a registry or vendor card proves for exact names: the family a fine-tune is built
 * on and the active size of a mixture-of-experts tag that omits it. Rows are added only with evidence and are matched
 * on every field they list, so a nearby name never inherits them. This is the one place that holds them; callers read
 * {@code ModelIdentity.baseFamily} and {@code activeB}.
 */
@Slf4j
final class AliasFacts {

    /**
     * A base-family row.
     *
     * @param family the parsed family
     * @param distillOf the family a distillation learned from, or null when the row is not about a distillation
     * @param version the generation, or null to match any
     * @param sizeB the size in billions, or null to match any
     * @param base the family it is built on
     */
    private record BaseRow(
            String family,
            @Nullable String distillOf,
            @Nullable String version,
            @Nullable Double sizeB,
            String base) {}

    /**
     * An active-size row.
     *
     * @param family the parsed family
     * @param version the generation, or null to match any
     * @param line the named line, or null to match any
     * @param sizeB the total size in billions, or null to match any
     * @param activeB the active parameters in billions
     */
    private record ActiveRow(
            String family,
            @Nullable String version,
            @Nullable String line,
            @Nullable Double sizeB,
            double activeB) {}

    // Each base family is proven by a model-card table or an HF base_model tag; never extended from a name alone.
    private static final List<BaseRow> BASES = List.of(
            new BaseRow("deepseek-r1-distill", "qwen", null, null, "qwen2.5"),
            new BaseRow("deepseek-r1-distill", "llama", null, null, "llama3.x"),
            new BaseRow("mimo-distill", "qwen", null, null, "qwen3.5"),
            new BaseRow("hermes", null, "4", 14.0, "qwen3"),
            new BaseRow("hermes", null, "4", 70.0, "llama3.1"),
            new BaseRow("mamaylm-gemma", null, null, null, "gemma"),
            new BaseRow("lapa", null, null, null, "gemma(arch)"));

    // gemma4:26b is the 26B-A4B mixture (same digest, Ollama tag page); gpt-oss:20b is 3.6B active (vendor card);
    // glm-4.7-flash is 30B with 3B active. gpt-oss:120b is left out until its vendor card is read.
    private static final List<ActiveRow> ACTIVE = List.of(
            new ActiveRow("gemma", "4", null, 26.0, 4.0),
            new ActiveRow("gpt-oss", null, null, 20.0, 3.6),
            new ActiveRow("glm", "4.7", "flash", null, 3.0));

    @Nullable
    String baseFamily(
            final @Nullable String family,
            final @Nullable String distillOf,
            final @Nullable String version,
            final @Nullable Double sizeB) {
        if (family == null) {
            return null;
        }
        final String base = BASES.stream()
                .filter(row -> row.family().equals(family) && matchesBase(row, distillOf, version, sizeB))
                .map(BaseRow::base)
                .findFirst()
                .orElse(null);
        log.debug("Base family family={} distillOf={} -> {}", family, distillOf, base);
        return base;
    }

    private static boolean matchesBase(
            final BaseRow row,
            final @Nullable String distillOf,
            final @Nullable String version,
            final @Nullable Double sizeB) {
        if (row.distillOf() != null) {
            return row.distillOf().equals(distillOf);
        }
        return (row.version() == null || row.version().equals(version))
                && (row.sizeB() == null || row.sizeB().equals(sizeB));
    }

    @Nullable
    Double activeSize(
            final @Nullable String family,
            final @Nullable String version,
            final @Nullable String line,
            final @Nullable Double sizeB) {
        final Double active = ACTIVE.stream()
                .filter(row -> row.family().equals(family)
                        && (row.version() == null || Objects.equals(row.version(), version))
                        && (row.line() == null || Objects.equals(row.line(), line))
                        && (row.sizeB() == null || Objects.equals(row.sizeB(), sizeB)))
                .map(ActiveRow::activeB)
                .findFirst()
                .orElse(null);
        log.debug("Active size family={} version={} size={} -> {}", family, version, sizeB, active);
        return active;
    }
}
