package ua.bookloom.pipeline;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The data part of an analysis call's user message, as a scripted model reads it: an analysis call's user message is
 * its task first — the instruction and the worked examples — and then the book's data under its heading (design D8), so
 * a fake that looked for names, windows or candidates in the whole message would also answer the examples.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AnalysisData {

    // The first line of the data part of every analysis task template.
    private static final List<String> HEADINGS =
            List.of("<Input>", "[Term", "[Candidates", "[Terms", "[Names and terms", "Candidates:");

    /**
     * The user message from the data part's heading on.
     *
     * @param user the user message of an analysis call
     * @return the data part, or the whole message when it has no data heading
     */
    public static String of(final String user) {
        final List<String> lines = user.lines().toList();
        final int start = firstHeading(lines);
        return String.join("\n", lines.subList(start, lines.size()));
    }

    private static int firstHeading(final List<String> lines) {
        return java.util.stream.IntStream.range(0, lines.size())
                .filter(index -> HEADINGS.stream().anyMatch(lines.get(index)::startsWith))
                .findFirst()
                .orElse(0);
    }
}
