package ua.bookloom.pipeline.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Turns the batch examples of a language file into the few-shot text of the JSON reply. A file states an example once,
 * as lines {@code source ⇒ target}; the numbering and the wire format are added here.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BatchExamples {

    private static final String ARROW = " ⇒ ";
    private static final String EXAMPLE_SEPARATOR = "\n\n";
    private static final Pattern EXAMPLE_SPLIT = Pattern.compile(EXAMPLE_SEPARATOR);

    /**
     * Renders every example.
     *
     * @param raw the examples as the language file states them, separated by a blank line; never blank
     * @return the examples as the prompt shows them
     */
    static String render(final String raw) {
        final List<String> rendered = new ArrayList<>();
        for (final String example : EXAMPLE_SPLIT.splitAsStream(raw).toList()) {
            final List<String[]> pairs =
                    example.lines().map(BatchExamples::split).toList();
            rendered.add(source(pairs) + "\n" + reply(pairs));
        }
        return String.join(EXAMPLE_SEPARATOR, rendered);
    }

    private static String[] split(final String line) {
        final int arrow = line.indexOf(ARROW);
        if (arrow < 0) {
            throw new IllegalStateException("A batch example line has no ⇒: " + line);
        }
        return new String[] {line.substring(0, arrow), line.substring(arrow + ARROW.length())};
    }

    private static String source(final List<String[]> pairs) {
        return "<Items>\n"
                + String.join(
                        "\n",
                        IntStream.range(0, pairs.size())
                                .mapToObj(i -> "<s id=\"" + (i + 1) + "\">" + pairs.get(i)[0] + "</s>")
                                .toList())
                + "\n</Items>";
    }

    private static String reply(final List<String[]> pairs) {
        final List<String> entries = IntStream.range(0, pairs.size())
                .mapToObj(i -> "{\"id\":\"" + (i + 1) + "\",\"target\":\"" + escape(pairs.get(i)[1]) + "\"}")
                .toList();
        return "Reply: {\"items\":[" + String.join(",", entries) + "]}";
    }

    private static String escape(final String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
