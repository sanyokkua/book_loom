package ua.bookloom.llm.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Reads the parts of a quant label that the table lookup needs: the dynamic prefix and the row candidates. */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class QuantLabel {

    private static final String DYNAMIC_PREFIX = "UD-";
    private static final Pattern SUFFIXED = Pattern.compile("^(Q\\d_K)_(?:XXS|XS|XL|L|M|S)$");
    private static final Pattern QAT_SUFFIX = Pattern.compile("^(.+)_QAT$");

    static boolean isDynamic(final String label) {
        return label.trim().toUpperCase(Locale.ROOT).startsWith(DYNAMIC_PREFIX);
    }

    /** The label without its dynamic prefix, upper-case. */
    static String bare(final String label) {
        final String upper = label.trim().toUpperCase(Locale.ROOT);
        return upper.startsWith(DYNAMIC_PREFIX) ? upper.substring(DYNAMIC_PREFIX.length()) : upper;
    }

    /** Row keys to try in order: the label itself, its {@code _M} reading for an {@code _L}/{@code _XL}, its base. */
    static List<String> candidates(final String label) {
        final String bare = bare(label);
        final List<String> keys = new ArrayList<>();
        keys.add(bare);
        if (bare.endsWith("_XL") || bare.endsWith("_L")) {
            keys.add(bare.substring(0, bare.lastIndexOf('_')) + "_M");
        }
        final Matcher suffixed = SUFFIXED.matcher(bare);
        if (suffixed.matches()) {
            keys.add(suffixed.group(1));
        }
        return keys;
    }

    /** The type a row rests on: {@code Q4_K} for {@code Q4_K_M}, {@code Q4_0} for {@code Q4_0_QAT}. */
    static String baseType(final String rowToken) {
        final Matcher suffixed = SUFFIXED.matcher(rowToken);
        if (suffixed.matches()) {
            return suffixed.group(1);
        }
        final Matcher qat = QAT_SUFFIX.matcher(rowToken);
        return qat.matches() ? qat.group(1) : rowToken;
    }
}
