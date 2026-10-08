package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Gender;

/**
 * Reads the chunk's character sheet ({@code name — gender}, with the pronoun evidence the sheet may append) and its
 * glossary renderings ({@code name → target}) into the characters a language check holds the target to. A character
 * whose rendering the chunk does not carry (a locked name is a protected token, not a rendering) is left out.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CharacterNames {

    private static final String SHEET_SEPARATOR = " — ";
    private static final String PAIR_SEPARATOR = " → ";

    /**
     * The characters of a sheet with the target spelling of their names.
     *
     * @param sheet the non-null {@code name — gender} lines
     * @param pairs the non-null {@code term → target} lines of the chunk's unlocked glossary renderings
     * @return one entry per sheet line whose gender is male or female and whose name has a rendering; never null
     */
    public static List<CharacterName> of(final List<String> sheet, final List<String> pairs) {
        Objects.requireNonNull(sheet, "sheet");
        Objects.requireNonNull(pairs, "pairs");
        final List<CharacterName> names = new ArrayList<>();
        for (final String line : sheet) {
            final int at = line.indexOf(SHEET_SEPARATOR);
            if (at <= 0) {
                continue;
            }
            final String term = line.substring(0, at).strip();
            final Gender gender = genderOf(line.substring(at + SHEET_SEPARATOR.length()));
            final String rendering = renderingOf(term, pairs);
            if (gender != Gender.UNKNOWN && !rendering.isEmpty()) {
                names.add(new CharacterName(rendering, gender));
            }
        }
        log.trace("Character names: {} of {} sheet line(s) have a rendering and a gender", names.size(), sheet.size());
        return List.copyOf(names);
    }

    private static Gender genderOf(final String text) {
        final String word = text.strip().split("[\\s(]", 2)[0].toLowerCase(Locale.ROOT);
        return switch (word) {
            case "female" -> Gender.FEMALE;
            case "male" -> Gender.MALE;
            default -> Gender.UNKNOWN;
        };
    }

    private static String renderingOf(final String term, final List<String> pairs) {
        for (final String pair : pairs) {
            final int at = pair.indexOf(PAIR_SEPARATOR);
            if (at > 0 && pair.substring(0, at).strip().equals(term)) {
                return pair.substring(at + PAIR_SEPARATOR.length()).strip();
            }
        }
        return "";
    }
}
