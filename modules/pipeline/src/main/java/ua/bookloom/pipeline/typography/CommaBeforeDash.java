package ua.bookloom.pipeline.typography;

import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.checks.QuotePair;

/** A comma written inside the closing quote of speech that a dash follows goes after the quote: {@code «Так,» —} to {@code «Так», —}. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CommaBeforeDash {

    static Edit apply(final String text, final QuotePair primary) {
        final String close = String.valueOf(primary.close());
        final Pattern pattern =
                Pattern.compile(",(?=" + Pattern.quote(close) + "[\\s\\u00A0]+[—–])" + Pattern.quote(close));
        return OutsideTokens.replaceAll(text, pattern, close + ",");
    }
}
