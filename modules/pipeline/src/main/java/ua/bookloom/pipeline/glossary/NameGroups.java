package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.index.Occurrences;

/**
 * Groups the scan's single-word candidates that are forms of one name ({@code Велемир}, {@code Велемира},
 * {@code Велемиру}) into one lemma candidate, by the source language's name-ending table ({@link NameForms}): forms
 * whose possible nominatives meet are one group, and {@link LemmaChoice} picks the group's nominative or leaves each
 * form as written. Runs of two or more words, and every candidate of a language with no table, are left as they are.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NameGroups {

    /**
     * The candidates with the forms of each name folded into one.
     *
     * @param tallies the candidates by term, in order of first occurrence; never null
     * @param read the scanned segments, where a shared form's mentions are decided; never null
     * @param language the source language tag, or null when it is not known (nothing is grouped)
     * @return the candidates, each group where its first form stood; never null
     */
    static Map<String, NameCandidate> group(
            final Map<String, NameCandidate> tallies,
            final List<Occurrences.Read> read,
            @Nullable final String language) {
        if (language == null || !NameForms.hasParadigm(language)) {
            log.debug("Name forms not grouped language={}: no name-ending table", language);
            return tallies;
        }
        final Map<String, Set<NameForms.Reading>> readings = new LinkedHashMap<>();
        tallies.keySet().stream()
                .filter(term -> term.indexOf(' ') < 0)
                .forEach(term -> readings.put(term, NameForms.candidates(term, language)));
        final LemmaChoice choice = new LemmaChoice(tallies, readings, read, language);
        final Map<String, List<NameCandidate>> byFirstForm = new HashMap<>();
        components(readings).forEach(forms -> byFirstForm.put(forms.getFirst(), choice.resolve(forms)));
        final Map<String, NameCandidate> grouped = new LinkedHashMap<>();
        tallies.forEach((term, tally) -> {
            if (!readings.containsKey(term)) {
                grouped.put(term, tally);
            }
            byFirstForm.getOrDefault(term, List.of()).forEach(found -> grouped.put(found.term(), found));
        });
        choice.logSummary(tallies.size(), grouped.size());
        return grouped;
    }

    // The forms joined, directly or through another form, by a nominative they could share; each in tally order.
    private static List<List<String>> components(final Map<String, Set<NameForms.Reading>> readings) {
        final Map<String, String> parent = new HashMap<>();
        final Map<String, String> formOfLemma = new HashMap<>();
        readings.forEach((form, forms) -> {
            parent.put(form, form);
            forms.forEach(reading -> {
                final String other = formOfLemma.putIfAbsent(Occurrences.keyOf(reading.lemma()), form);
                if (other != null) {
                    parent.put(root(parent, form), root(parent, other));
                }
            });
        });
        final Map<String, List<String>> groups = new LinkedHashMap<>();
        readings.keySet()
                .forEach(form -> groups.computeIfAbsent(root(parent, form), _ -> new ArrayList<>())
                        .add(form));
        return List.copyOf(groups.values());
    }

    private static String root(final Map<String, String> parent, final String form) {
        String at = form;
        String up = parent.getOrDefault(at, at);
        while (!up.equals(at)) {
            at = up;
            up = parent.getOrDefault(at, at);
        }
        return at;
    }
}
