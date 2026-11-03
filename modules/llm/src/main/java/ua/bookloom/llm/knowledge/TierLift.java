package ua.bookloom.llm.knowledge;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.QuantTier;
import ua.bookloom.api.llm.SizeClass;

/** The Unsloth Dynamic rule: a dynamic build of a 3-bit-or-lower base is worth one tier more (design D22). */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TierLift {

    private static final int LIFT_UP_TO_BITS = 3;

    static QuantTier apply(
            final QuantTier tier,
            final boolean dynamic,
            final double bpw,
            final String baseType,
            final SizeClass size) {
        if (!dynamic || (int) Math.floor(bpw) > LIFT_UP_TO_BITS || !isLiftable(tier)) {
            return tier;
        }
        if (isCapped(baseType, size)) {
            return tier;
        }
        return switch (tier) {
            case UNUSABLE -> QuantTier.POOR;
            case POOR -> QuantTier.ACCEPTABLE;
            case ACCEPTABLE -> QuantTier.GOOD;
            case GOOD -> QuantTier.EXCELLENT;
            case EXCELLENT, UNMEASURED, REMOVED -> tier;
        };
    }

    private static boolean isLiftable(final QuantTier tier) {
        return tier != QuantTier.UNMEASURED && tier != QuantTier.REMOVED;
    }

    private static boolean isCapped(final String baseType, final SizeClass size) {
        final boolean lowBitFamily =
                baseType.startsWith("IQ1") || baseType.startsWith("IQ2") || baseType.startsWith("TQ");
        return lowBitFamily && (size == SizeClass.SMALL || size == SizeClass.MID);
    }
}
