package ua.bookloom.pipeline;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.FaultyModel.Fault;

/** Damages a draft's target the way a small model does: a lost, doubled or misspelt placeholder, a stray bracket. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FaultyText {

    private static final Pattern TOKEN = Pattern.compile("⟦g\\d+⟧");

    static String damage(final Fault fault, final String target, final Random random) {
        final Matcher token = TOKEN.matcher(target);
        final boolean hasToken = token.find();
        return switch (fault) {
            case EMPTY_TARGET -> "";
            case REFUSAL -> "I'm sorry, but I can't help with translating this text.";
            case STRAY_BRACKET -> target + " ⟦";
            case DROP_TOKEN -> hasToken ? target.substring(0, token.start()) + target.substring(token.end()) : target;
            case DUPLICATE_TOKEN -> hasToken ? target + " " + token.group() : target + " ⟦g" + random.nextInt(9) + "⟧";
            case GARBLE_TOKEN ->
                hasToken
                        ? target.substring(0, token.start())
                                + token.group().replace("⟦g", "[[g")
                                + target.substring(token.end())
                        : target + " ⟦g⟧";
            default -> target;
        };
    }
}
