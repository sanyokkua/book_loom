package ua.bookloom.pipeline.qa;

import static ua.bookloom.pipeline.qa.CheckName.LENGTH;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The length-ratio check: whether the target's length, against its source, sits inside the pair's expected band. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LengthCheck {

    private static final double MARGIN_WINDOW_FACTOR = 0.10;

    static CheckResult run(final SoftCheckInput input) {
        Objects.requireNonNull(input, "input");
        final int sourceChars = codePointCount(input.sourceDisplayText());
        if (sourceChars == 0) {
            return CheckResult.skip(LENGTH);
        }
        final int targetChars = codePointCount(input.targetDisplayText());
        final double ratio = (double) targetChars / sourceChars;
        final LengthBand band = LengthBand.forPair(input.sourceLanguage(), input.targetLanguage())
                .widenedFor(sourceChars);
        if (ratio < band.lower() || ratio > band.upper()) {
            return CheckResult.fail(
                    LENGTH, "length ratio " + ratio + " outside [" + band.lower() + "," + band.upper() + "]");
        }
        return CheckResult.pass(LENGTH, marginWithinBand(ratio, band));
    }

    private static double marginWithinBand(final double ratio, final LengthBand band) {
        final double distanceToNearerBound = Math.min(ratio - band.lower(), band.upper() - ratio);
        final double window = MARGIN_WINDOW_FACTOR * (band.upper() - band.lower());
        return Margins.clamp(distanceToNearerBound / window);
    }

    private static int codePointCount(final String text) {
        return text.codePointCount(0, text.length());
    }
}
