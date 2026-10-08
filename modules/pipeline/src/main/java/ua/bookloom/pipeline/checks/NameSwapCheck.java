package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.glossary.KeyTermScan;
import ua.bookloom.pipeline.lexicon.TermMappingVerifier;
import ua.bookloom.pipeline.lexicon.TermMatch;

/**
 * Whether a glossary name has been replaced by another glossary name: the source names A, the target has no A, and it
 * has B, whom the source does not name. A name dropped for a pronoun is not a swap, so only the replacement is
 * reported. Blocking, since a different person in the sentence changes the story. The target may decline either name,
 * by the same stem match the vocative check uses.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NameSwapCheck {

    private static final String ARROW = " → ";

    private record Named(String term, String rendering) {}

    static List<CheckFinding> find(
            final String source,
            final String target,
            final List<String> glossaryPairs,
            final List<String> alternations,
            @Nullable final String sourceLanguage) {
        final List<Named> names = names(glossaryPairs, sourceLanguage);
        log.trace("Name swap check over {} glossary name(s)", names.size());
        final List<CheckFinding> found = new ArrayList<>();
        for (final Named lost : names) {
            if (!TermMatch.isNamedIn(lost.term(), source) || holds(lost, target, alternations)) {
                continue;
            }
            final Optional<Named> instead = names.stream()
                    .filter(other -> !other.equals(lost) && !overlap(other, lost))
                    .filter(other -> !TermMatch.isNamedIn(other.term(), source))
                    .filter(other -> holds(other, target, alternations))
                    .findFirst();
            instead.ifPresent(other -> {
                log.debug("Name swap: the source names one glossary name and the target another");
                log.trace("Name swap {} → {} instead of {}", lost.term(), other.rendering(), lost.rendering());
                found.add(finding(lost, other));
            });
        }
        return List.copyOf(found);
    }

    private static List<Named> names(final List<String> pairs, @Nullable final String sourceLanguage) {
        final List<Named> names = new ArrayList<>();
        for (final String pair : pairs) {
            final int at = pair.indexOf(ARROW);
            if (at <= 0) {
                continue;
            }
            final String term = pair.substring(0, at).strip();
            final String rendering = pair.substring(at + ARROW.length()).strip();
            if (!term.isEmpty()
                    && Character.isUpperCase(term.codePointAt(0))
                    && !rendering.isEmpty()
                    && !KeyTermScan.isTitle(term, sourceLanguage)) {
                names.add(new Named(term, rendering));
            }
        }
        return names;
    }

    private static boolean holds(final Named name, final String target, final List<String> alternations) {
        return !TermMappingVerifier.verify(
                        Map.of(name.term(), name.rendering()), List.of(name.term()), name.term(), target, alternations)
                .isEmpty();
    }

    // One rendering inside the other (Смит, Смітсон) cannot tell which name stands in the target.
    private static boolean overlap(final Named left, final Named right) {
        final String first = left.rendering().toLowerCase(Locale.ROOT);
        final String second = right.rendering().toLowerCase(Locale.ROOT);
        return first.contains(second) || second.contains(first);
    }

    private static CheckFinding finding(final Named lost, final Named instead) {
        return new CheckFinding(
                FindingKind.NAME_SWAP,
                new TextSpan(0, 0, ""),
                "The source names \"" + lost.term() + "\" but the translation has \"" + instead.rendering()
                        + "\" in its place: write \"" + lost.rendering() + "\", in the form the sentence needs.",
                true);
    }
}
