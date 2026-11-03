package ua.bookloom.llm.profile;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelIdentity;
import ua.bookloom.api.llm.ModelKind;
import ua.bookloom.api.llm.ModelReport;

/**
 * Which parsed names a built-in row speaks for. It reads {@link ModelIdentity} only, never the raw id and never the
 * server's architecture: an architecture is shared by unrelated families, so it cannot name a row.
 *
 * @param family the whole family name ({@code nemotron-bolt} is not {@code nemotron}); non-null
 * @param version the generation as the parser writes it, or null when any (or none) will do
 * @param line the named line inside a generation, or null for a name that has none
 * @param sizes the total sizes in billions the row covers; never null, empty when the row does not name a size
 * @param effB the effective size of an "e4b" style name, or null
 * @param allowedVariants the variant words a covered name may carry; a name with another variant (coder, vision,
 *     reasoning, thinking) is a different model and falls to its own row or the default; never null
 * @param requiredVariants the variant words a covered name must carry; never null
 */
public record IdentityPattern(
        String family,
        @Nullable String version,
        @Nullable String line,
        Set<Double> sizes,
        @Nullable Double effB,
        Set<String> allowedVariants,
        Set<String> requiredVariants) {

    private static final double SIZE_TOLERANCE = 0.01;
    private static final double LARGE_FROM_B = 14.0;
    private static final String INSTRUCT = "instruct";

    public IdentityPattern {
        Objects.requireNonNull(family, "family");
        sizes = Set.copyOf(Objects.requireNonNull(sizes, "sizes"));
        allowedVariants = Set.copyOf(Objects.requireNonNull(allowedVariants, "allowedVariants"));
        requiredVariants = Set.copyOf(Objects.requireNonNull(requiredVariants, "requiredVariants"));
    }

    /**
     * A pattern for a whole family that takes only a plain or instruct name.
     *
     * @param family the family name as the parser writes it; non-null
     * @return the pattern, to be narrowed with the {@code with…} methods
     */
    public static IdentityPattern family(final String family) {
        return new IdentityPattern(family, null, null, Set.of(), null, Set.of(INSTRUCT), Set.of());
    }

    /** Narrows to one generation. */
    public IdentityPattern version(final String generation) {
        return new IdentityPattern(family, generation, line, sizes, effB, allowedVariants, requiredVariants);
    }

    /** Narrows to one named line. */
    public IdentityPattern line(final String named) {
        return new IdentityPattern(family, version, named, sizes, effB, allowedVariants, requiredVariants);
    }

    /** Narrows to these total sizes in billions. */
    public IdentityPattern sizes(final Double... billions) {
        return new IdentityPattern(family, version, line, Set.of(billions), effB, allowedVariants, requiredVariants);
    }

    /** Narrows to one effective size, as an "e4b" name writes it. */
    public IdentityPattern effective(final double billions) {
        return new IdentityPattern(family, version, line, sizes, billions, allowedVariants, requiredVariants);
    }

    /** Takes exactly this variant, which a name must carry, and the instruct variant a name may carry. */
    public IdentityPattern requiring(final String variant) {
        return new IdentityPattern(family, version, line, sizes, effB, Set.of(INSTRUCT, variant), Set.of(variant));
    }

    /** Takes names that carry these variants beside the instruct variant. */
    public IdentityPattern allowing(final String... variants) {
        final Set<String> allowed = Arrays.stream(variants).collect(Collectors.toSet());
        allowed.add(INSTRUCT);
        return new IdentityPattern(family, version, line, sizes, effB, allowed, requiredVariants);
    }

    /**
     * Whether the pattern speaks for a name.
     *
     * @param identity the parsed name; non-null
     * @return {@code true} when the family, generation, line, size and variants all agree
     */
    public boolean matches(final ModelIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        return identity.kind() == ModelKind.LLM
                && family.equals(identity.family())
                && (version == null || version.equals(identity.version()))
                && Objects.equals(line, identity.line())
                && sizeAgrees(identity)
                && allowedVariants.containsAll(modelVariants(identity))
                && identity.variants().containsAll(requiredVariants);
    }

    // qat and imatrix describe the quant file, not the model: a row speaks for the model in either build.
    private static List<String> modelVariants(final ModelIdentity identity) {
        return identity.variants().stream()
                .filter(variant -> !"qat".equals(variant) && !"imatrix".equals(variant))
                .toList();
    }

    /**
     * How specific the pattern is: how much of a name it pins down.
     *
     * @return the number of constraints beyond the family
     */
    public int specificity() {
        return (version == null ? 0 : 1)
                + (line == null ? 0 : 1)
                + (sizes.isEmpty() && effB == null ? 0 : 1)
                + requiredVariants.size();
    }

    /**
     * How a model this pattern speaks for is reported as matched.
     *
     * @return {@link ModelReport.Match#EXACT} when a size is named, else the generation, else the family
     */
    public ModelReport.Match match() {
        if (!sizes.isEmpty() || effB != null) {
            return ModelReport.Match.EXACT;
        }
        return version == null ? ModelReport.Match.FAMILY : ModelReport.Match.FAMILY_VERSION;
    }

    /**
     * The size class the pattern's models belong to.
     *
     * @return {@code true} when the largest size the pattern names is 14 billion or more
     */
    public boolean isLarge() {
        final double largest =
                sizes.stream().mapToDouble(Double::doubleValue).max().orElse(effB == null ? 0.0 : effB);
        return largest >= LARGE_FROM_B;
    }

    private boolean sizeAgrees(final ModelIdentity identity) {
        return totalAgrees(identity.sizeB()) && effectiveAgrees(identity.effB());
    }

    private boolean totalAgrees(final @Nullable Double total) {
        if (sizes.isEmpty()) {
            return true;
        }
        return total != null && sizes.stream().anyMatch(s -> isClose(s, total));
    }

    private boolean effectiveAgrees(final @Nullable Double effective) {
        if (effB == null) {
            return true;
        }
        return effective != null && isClose(effB, effective);
    }

    private static boolean isClose(final double a, final double b) {
        return Math.abs(a - b) < SIZE_TOLERANCE;
    }
}
