package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.glossary.KeyTermScan;
import ua.bookloom.pipeline.lexicon.TermMappingVerifier;

/**
 * Whether a glossary name the source calls out — set off by a comma at the start or end of a sentence, {@code Finn, you
 * never listen} / {@code you never listen, Finn?} — is still in the target. A name in that position is the easiest to
 * lose: the sentence reads whole without it, so no length check notices. The name may be declined, so the target is
 * held to the same stem match the lexicon uses. Blocking.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class VocativeCheck {

    private static final String ARROW = " → ";
    private static final String CLOSERS = "[\\s.!?…»”\"’)]*";

    static List<CheckFinding> find(
            final String source,
            final String target,
            final List<String> glossaryPairs,
            final List<String> alternations,
            @Nullable final String sourceLanguage) {
        final List<CheckFinding> found = new ArrayList<>();
        for (final String pair : glossaryPairs) {
            final int at = pair.indexOf(ARROW);
            if (at <= 0) {
                continue;
            }
            final String term = pair.substring(0, at).strip();
            final String rendering = pair.substring(at + ARROW.length()).strip();
            if (!isName(term)
                    || KeyTermScan.isTitle(term, sourceLanguage)
                    || rendering.isEmpty()
                    || !isCalledOut(source, term)) {
                continue;
            }
            if (TermMappingVerifier.verify(Map.of(term, rendering), List.of(term), source, target, alternations)
                    .isEmpty()) {
                log.debug("Vocative check: a called-out name is missing from the target");
                found.add(new CheckFinding(
                        FindingKind.VOCATIVE_MISSING,
                        new TextSpan(0, 0, ""),
                        "The source addresses \"" + term + "\" by name and the translation does not: put \"" + rendering
                                + "\" back, in the form the sentence needs.",
                        true));
            }
        }
        return found;
    }

    // Only a capitalised term is a name: a title or a common word (master, sir) is the lexicon's, and its drift is not
    // a loss.
    private static boolean isName(final String term) {
        return !term.isEmpty() && Character.isUpperCase(term.codePointAt(0));
    }

    private static boolean isCalledOut(final String source, final String term) {
        final String name = Pattern.quote(term);
        final Pattern atEnd = Pattern.compile("[,;]\\s+" + name + CLOSERS + "$");
        final Pattern atStart = Pattern.compile("^[“\"«—–-]?\\s*" + name + "\\s*,");
        return Sentences.split(source).stream()
                .anyMatch(sentence -> atEnd.matcher(sentence).find()
                        || atStart.matcher(sentence).find());
    }
}
