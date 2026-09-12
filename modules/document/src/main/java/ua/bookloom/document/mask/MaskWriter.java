package ua.bookloom.document.mask;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The mutable accumulator a mask walk appends to — the single place a token is minted, so first-appearance order
 * and dense numbering are properties of the walk itself rather than of a later sort or renumbering pass.
 *
 * <p>Public rather than package-private, and deliberately so: {@code TreeMasker} — the tree-shaped formats'
 * masker — lives in {@code ua.bookloom.document.model}, not in this package (design.md D10's package split is
 * overridden here; see {@code TreeMasker}'s class Javadoc for why), and it is this accumulator, not a duplicate
 * of it, that must mint every token so first-appearance order and dense numbering hold across the whole walk.
 * Neither this package nor {@code ua.bookloom.document.model} is exported past the module boundary, so this stays
 * internal to {@code :document} regardless.
 */
public final class MaskWriter {

    private final StringBuilder masked = new StringBuilder();
    private final Map<String, String> placeholders = new LinkedHashMap<>();
    private int nextIndex;

    /**
     * Appends {@code text} as character data, except that a literal U+27E6 {@code ⟦} or U+27E7 {@code ⟧}
     * occurrence is captured as its own one-character atomic protected span rather than appended verbatim (the
     * requirement <em>Protect a placeholder bracket that occurs in the source text</em>). Callers append character
     * data through this method only after every protected span has already been captured through
     * {@link #appendAtomic(String)}, which is what keeps a bracket inside an inline code span or an attribute
     * value out of the scan — those fragments never reach this method as character data (design.md D4).
     *
     * @param text the character data to append; never null
     */
    public void appendCharacterData(String text) {
        Objects.requireNonNull(text, "text");
        int runStart = 0;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '⟦' || c == '⟧') {
                masked.append(text, runStart, i);
                appendAtomic(String.valueOf(c));
                runStart = i + 1;
            }
        }
        masked.append(text, runStart, text.length());
    }

    /**
     * Mints the next token, records {@code fragment} as the exact source fragment it replaces, appends the token
     * to the masked form, and returns it.
     *
     * @param fragment the exact fragment this token replaces; never null
     * @return the minted token, e.g. {@code "⟦g0⟧"}; never null
     */
    public String appendAtomic(String fragment) {
        Objects.requireNonNull(fragment, "fragment");
        final String token = Placeholders.token(nextIndex);
        placeholders.put(Placeholders.key(nextIndex), fragment);
        nextIndex++;
        masked.append(token);
        return token;
    }

    /**
     * Runs the mask-time invariant — every emitted token unique in the masked form, and the placeholder map a
     * bijection over exactly the tokens present — scanning the masked form only, never the mapped fragments, so a
     * code span whose own text is the literal {@code ⟦g0⟧} does not trip the check (design.md D4, the requirement
     * <em>Assert the placeholder invariant at mask time</em>).
     *
     * @return the built {@link MaskedContent}; never null
     * @throws MaskInvariantException if a token repeats in the masked form, or the map's keys are not exactly the
     *     set of tokens present
     */
    public MaskedContent build() {
        final String result = masked.toString();
        final Map<String, Integer> counts = Placeholders.multisetOf(result);
        final Set<String> duplicated = new LinkedHashSet<>();
        final Set<String> foundKeys = new LinkedHashSet<>();
        for (final Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 1) {
                duplicated.add(entry.getKey());
            }
            foundKeys.add(Placeholders.keyOf(entry.getKey()));
        }
        if (!duplicated.isEmpty()) {
            throw new MaskInvariantException("Duplicate placeholder token(s) in masked form: " + duplicated);
        }
        if (!foundKeys.equals(placeholders.keySet())) {
            throw new MaskInvariantException("Placeholder map is not a bijection over the masked form's tokens: "
                    + "found=" + foundKeys + " mapped=" + placeholders.keySet());
        }
        return new MaskedContent(result, placeholders);
    }
}
