package ua.bookloom.pipeline.glossary;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The model replies of the recurring-term reading (15i.N7), built by hand for the tests that script a model: public so
 * the service tests of other packages answer a reading the same way.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LexiconReplies {

    private static final Pattern MARKED = Pattern.compile("\\[\\[(.+?)]]");
    private static final int WINDOWS = 6;

    /**
     * A reading reply for windows 1 to 6, the quote of each being the term itself (it stands in every window); the
     * reader drops the numbers a call did not show.
     *
     * @param term the term as the book writes it
     * @param usage {@code term}, {@code word} or {@code name}
     * @param senses how many senses the windows show
     * @param rendering the one rendering, or empty
     * @return the reply's JSON
     */
    public static String reading(final String term, final String usage, final int senses, final String rendering) {
        final String windows = IntStream.rangeClosed(1, WINDOWS)
                .mapToObj(number -> "{\"window\":%d,\"quote\":\"%s\",\"usage\":\"%s\"}".formatted(number, term, usage))
                .reduce((first, second) -> first + "," + second)
                .orElseThrow();
        return "{\"windows\":[" + windows + "],\"senses\":" + senses + ",\"rendering\":\"" + rendering + "\"}";
    }

    /**
     * The term a reading prompt is about: the text of the first window's mark.
     *
     * @param user the prompt's user message
     * @return the marked text, or empty when nothing is marked
     */
    public static String termOf(final String user) {
        final Matcher marked = MARKED.matcher(user);
        return marked.find() ? marked.group(1) : "";
    }
}
