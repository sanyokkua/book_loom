package ua.bookloom.util.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The one comparison key of a glossary term, shared by the repository, the scans, the Add term dialog and the CSV
 * import, so {@code Lovelace}, {@code LOVELACE}, {@code Lovelace's} and {@code "Lovelace,"} are one term everywhere.
 *
 * <p>Before this key each way in compared differently (exact lower case in one place, {@code equalsIgnoreCase} in
 * another, NFC in a third), so a name could be held twice and a removed name could come back in another spelling.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GlossaryKeys {

    private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$");
    private static final Pattern POSSESSIVE = Pattern.compile("['’ʼ][sS]$");
    private static final Pattern SPACES = Pattern.compile("[\\p{Z}\\s]+");

    /**
     * The key a term is compared by.
     *
     * @param term the term as written; never null
     * @return the NFC form with edge punctuation and a trailing possessive {@code 's}/{@code ’s} dropped, inner spaces
     *     collapsed to one and the case folded; a term with no letter or digit keys as its folded, stripped self
     */
    public static String of(final String term) {
        Objects.requireNonNull(term, "term");
        final String composed = Normalizer.normalize(term, Normalizer.Form.NFC);
        final String trimmed = EDGE_PUNCTUATION.matcher(composed).replaceAll("");
        final String bare = EDGE_PUNCTUATION
                .matcher(POSSESSIVE.matcher(trimmed).replaceFirst(""))
                .replaceAll("");
        final String kept = bare.isEmpty() ? composed.strip() : bare;
        return fold(SPACES.matcher(kept).replaceAll(" "));
    }

    // Upper-casing first folds the forms lower-casing alone keeps apart, such as ß and SS.
    private static String fold(final String text) {
        return text.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
    }
}
