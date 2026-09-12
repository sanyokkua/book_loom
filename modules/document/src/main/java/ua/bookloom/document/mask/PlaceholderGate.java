package ua.bookloom.document.mask;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The placeholder-multiset hard gate — the requirement <em>Compare the placeholder multiset as a hard gate before
 * restoring anything</em>. Compares the multiset of {@code ⟦gN⟧} tokens in a target against the multiset in a
 * segment's masked form; token order never affects the outcome, but a missing, added, or duplicated token does.
 *
 * <p>This is the one check restoring performs before touching a placeholder — it reports, it never repairs. A
 * caller that sees {@link GateOutcome#matches()} {@code false} restores nothing and alters nothing.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlaceholderGate {

    /**
     * Compares {@code target}'s placeholder multiset against {@code expectedMasked}'s.
     *
     * @param expectedMasked the segment's masked form, the source of truth for which tokens may appear; never null
     * @param target the text to validate — a translated target, supplied back to restore; never null
     * @return the comparison outcome, carrying both token lists in full; never null
     */
    public static GateOutcome compare(String expectedMasked, String target) {
        Objects.requireNonNull(expectedMasked, "expectedMasked");
        Objects.requireNonNull(target, "target");
        final boolean matches = Placeholders.multisetOf(expectedMasked).equals(Placeholders.multisetOf(target));
        return new GateOutcome(matches, Placeholders.tokensOf(expectedMasked), Placeholders.tokensOf(target));
    }
}
