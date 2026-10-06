package ua.bookloom.pipeline.eval;

import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Whether a batch target carries the protocol around it: the {@code terms} object, an {@code id} entry of the reply or a
 * code fence, which a model leaks into the text when it loses track of the JSON string it is writing. This is the
 * eval's measurement; the run's own rejection is task 15e.6.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LeakCheck {

    private static final Pattern LEAK = Pattern.compile("[\"«“]terms[\"»”]|\\{[^}]*[\"«“]id[\"»”]|```");

    static boolean leaks(final String target) {
        return LEAK.matcher(target).find();
    }
}
