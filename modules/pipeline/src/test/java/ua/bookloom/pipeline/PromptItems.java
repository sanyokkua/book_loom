package ua.bookloom.pipeline;

import java.util.List;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Reads the items of a translate-family prompt as a scripted model sees them: the {@code <s id="n">…</s>} lines of the
 * last {@code <Items>} block of the user message, a single draft being a batch of one.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PromptItems {

    private static final Pattern ITEM = Pattern.compile("<s id=\"([^\"]+)\">(.*?)</s>", Pattern.DOTALL);
    private static final String OPEN = "<Items>";

    /** The items' texts in order; empty when the message holds no {@code <Items>} block. */
    public static List<String> of(final String userMessage) {
        final int block = userMessage.lastIndexOf(OPEN);
        return block < 0
                ? List.of()
                : ITEM.matcher(userMessage.substring(block))
                        .results()
                        .map(found -> found.group(2))
                        .toList();
    }

    /** The first item's text, or empty when the message holds none. */
    public static String first(final String userMessage) {
        final List<String> items = of(userMessage);
        return items.isEmpty() ? "" : items.getFirst();
    }
}
