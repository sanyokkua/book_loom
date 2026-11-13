package ua.bookloom.pipeline.eval;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The brief and label texts of an eval report's provenance. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EvalBriefLabel {

    private static final String[] BRIEF_KEYS = {
        "PRESET", "GOLD", "BRIEF", "REGISTER", "NAMES", "GENRE", "CHAPTER_SUMMARIES"
    };

    static String briefSummary(final Map<String, String> env) {
        final StringBuilder out = new StringBuilder();
        for (final String key : BRIEF_KEYS) {
            final String value = env.get("BOOKLOOM_EVAL_" + key);
            if (value != null && !value.isBlank()) {
                final String shown = "BRIEF".equals(key) || "GOLD".equals(key)
                        ? Path.of(value).getFileName().toString()
                        : value;
                out.append(out.isEmpty() ? "" : " ")
                        .append(key.toLowerCase(java.util.Locale.ROOT))
                        .append('=')
                        .append(shown);
            }
        }
        return out.isEmpty() ? "defaults" : out.toString();
    }

    static String joinLabel(final String first, final String second) {
        return Stream.of(first, second)
                .filter(part -> !part.isBlank())
                .collect(java.util.stream.Collectors.joining("-"));
    }
}
