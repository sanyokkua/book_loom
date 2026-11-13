package ua.bookloom.pipeline.glossary;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.BriefChoices;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * The spelling conventions a target language declares ({@code conventions=hardG} with {@code convention.hardG.choices}
 * and {@code convention.hardG.default}) and the choices the Brief stores (design D6). No code names a letter: the
 * letters come from the language file, so a convention belongs to the language, not to a pair.
 */
@Slf4j
final class TargetConventions {

    private static final Pattern CAMEL = Pattern.compile("(?<=\\p{Ll})(?=\\p{Lu})");

    /**
     * One convention the target language declares and the choice the Brief stores.
     *
     * @param name the convention's key ({@code hardG})
     * @param choices the lower-case letters it chooses between
     * @param fallback the file's default letter, the unmarked spelling
     * @param chosen the Brief's letter, or null when the Brief stores none
     */
    private record Convention(
            String name,
            List<String> choices,
            String fallback,
            @Nullable String chosen) {}

    private final List<Convention> declared;

    private TargetConventions(final List<Convention> declared) {
        this.declared = List.copyOf(declared);
    }

    /**
     * The conventions of a target language with the Brief's choices.
     *
     * @param targetLanguage the non-null target language tag
     * @param chosen the Brief's choices by convention name; never null
     * @return the conventions; none when the language declares none
     */
    static TargetConventions of(final String targetLanguage, final Map<String, String> chosen) {
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        Objects.requireNonNull(chosen, "chosen");
        final List<Convention> declared = new ArrayList<>();
        for (final BriefChoices.DeclaredConvention convention :
                LanguageRules.bundled().briefChoices(targetLanguage).conventions()) {
            final String stored = chosen.get(convention.key());
            declared.add(new Convention(
                    convention.key(),
                    convention.choices().stream()
                            .map(letter -> letter.toLowerCase(Locale.ROOT))
                            .toList(),
                    convention.fallback().toLowerCase(Locale.ROOT),
                    stored == null ? null : stored.toLowerCase(Locale.ROOT)));
        }
        log.debug("Target conventions language={} declared={} chosen={}", targetLanguage, declared.size(), chosen);
        return new TargetConventions(declared);
    }

    /**
     * Why a target goes against a convention the Brief stores. The file's default is the unmarked spelling and never
     * refused: only the marked alternative is a choice ({@code г} may stand for an h in any book).
     *
     * @param candidate the non-null target
     * @return the reason ({@code hard g is spelled ґ, the book uses г}), or empty when it follows every stored choice
     */
    Optional<String> refusal(final String candidate) {
        final String lower = candidate.toLowerCase(Locale.ROOT);
        for (final Convention convention : declared) {
            final String chosen = convention.chosen();
            if (chosen == null) {
                continue;
            }
            final Optional<String> other = convention.choices().stream()
                    .filter(letter -> !letter.equals(chosen) && !letter.equals(convention.fallback()))
                    .filter(lower::contains)
                    .findFirst();
            if (other.isPresent()) {
                return Optional.of(
                        labelOf(convention.name()) + " is spelled " + other.get() + ", the book uses " + chosen);
            }
        }
        return Optional.empty();
    }

    /**
     * The text to compare forms by: the letters one convention chooses between are one letter ({@code Гарсія} and
     * {@code Ґарсії} are one name).
     *
     * @param text the non-null text
     * @return the text in NFC, lower case, each choice letter replaced by the file's default
     */
    String fold(final String text) {
        String folded = Normalizer.normalize(text.strip(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        for (final Convention convention : declared) {
            for (final String letter : convention.choices()) {
                folded = folded.replace(letter, convention.fallback());
            }
        }
        return folded;
    }

    // hardG reads "hard g": the key's words, lower-cased.
    private static String labelOf(final String name) {
        return String.join(" ", CAMEL.split(name)).toLowerCase(Locale.ROOT);
    }
}
