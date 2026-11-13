package ua.bookloom.llm.knowledge;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelIdentity;
import ua.bookloom.api.llm.SizeClass;

/** Puts a model into the size band its quant is judged in (design D22). */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SizeClassifier {

    static final double SMALL_MAX_B = 5.0;
    static final double MID_MAX_B = 16.0;
    private static final Pattern PARAMETERS = Pattern.compile("(\\d+(?:\\.\\d+)?) ?([KMBT]?)");
    private static final double PER_MILLION = 1_000.0;
    private static final double PER_TRILLION = 1_000.0;

    /** The band, or null when neither a total nor an active size is known. */
    static @Nullable SizeClass classify(final ModelIdentity identity, final @Nullable ModelFacts facts) {
        final Double total = totalB(identity, facts);
        final Double active = identity.activeB();
        if (active != null && active <= SMALL_MAX_B) {
            return SizeClass.MOE;
        }
        if (total == null) {
            return active == null ? null : SizeClass.LARGE;
        }
        if (total <= SMALL_MAX_B) {
            return SizeClass.SMALL;
        }
        return total <= MID_MAX_B ? SizeClass.MID : SizeClass.LARGE;
    }

    private static @Nullable Double totalB(final ModelIdentity identity, final @Nullable ModelFacts facts) {
        if (identity.effB() != null) {
            return identity.effB();
        }
        final Double reported = facts == null ? null : parseBillions(facts.parameterSize());
        return reported != null ? reported : identity.sizeB();
    }

    /** Reads "9.7B", "270M" and the like as billions; null for anything else. */
    static @Nullable Double parseBillions(final @Nullable String text) {
        if (text == null) {
            return null;
        }
        final Matcher matcher = PARAMETERS.matcher(text.strip().toUpperCase(Locale.ROOT));
        if (!matcher.matches()) {
            return null;
        }
        final double value = Double.parseDouble(matcher.group(1));
        return switch (matcher.group(2)) {
            case "M" -> value / PER_MILLION;
            case "K" -> value / (PER_MILLION * PER_MILLION);
            case "T" -> value * PER_TRILLION;
            default -> value;
        };
    }
}
