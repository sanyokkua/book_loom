package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.index.Occurrences;

/**
 * Picks the nominative of one group of forms ({@link NameGroups}): the nominative the most forms could belong to, then
 * one the book writes, then the commonest, then the earliest. It is accepted when the book writes it and another form
 * agrees (or it is the group's only form), or when two forms agree on it though the book never writes it — then it is
 * marked inferred; one oblique form alone stays as written, and a nominative on the language's title list is never a
 * name. The group's count is the sum of its forms', so the scan's floor counts the group.
 *
 * <p>A form that is also the nominative of another name ({@code Велемира}, a woman, beside the man {@code Велемир}'s
 * genitive) becomes a candidate of its own only when the clause decides at least one of its mentions for that name
 * ({@link ClauseEvidence}); its unsure mentions count for both, and it is no longer listed among the lemma's forms,
 * since one key names one entry.
 */
@Slf4j
final class LemmaChoice {

    private static final Comparator<Lemma> ORDER = Comparator.comparingInt((Lemma lemma) -> -lemma.forms.size())
            .thenComparing(lemma -> !lemma.attested)
            .thenComparingInt(lemma -> -lemma.count)
            .thenComparingInt(lemma -> lemma.first);

    private final Map<String, NameCandidate> tallies;
    private final Map<String, Set<NameForms.Reading>> readings;
    private final List<Occurrences.Read> read;
    private final Set<String> titles;
    private final ClauseEvidence clause;
    private final String language;
    private int groups;
    private int formsMerged;
    private int inferred;
    private int competitors;

    LemmaChoice(
            final Map<String, NameCandidate> tallies,
            final Map<String, Set<NameForms.Reading>> readings,
            final List<Occurrences.Read> read,
            final String language) {
        this.tallies = tallies;
        this.readings = readings;
        this.read = read;
        this.language = language;
        this.titles = StopWords.titles(language);
        this.clause = ClauseEvidence.of(language);
    }

    /** One possible nominative of a group, with the forms that could belong to it. */
    private static final class Lemma {
        private final String key;
        private String display;
        private final Set<String> forms = new LinkedHashSet<>();
        private int count;
        private final int first;
        private boolean attested;

        Lemma(final String key, final String display, final int first) {
            this.key = key;
            this.display = display;
            this.first = first;
        }
    }

    /**
     * The candidates one group leaves.
     *
     * @param forms the group's forms in order of first occurrence; never empty
     * @return the lemma candidate and any competitor, then the forms the lemma does not take; never empty
     */
    List<NameCandidate> resolve(final List<String> forms) {
        final Lemma best = lemmasOf(forms).values().stream().min(ORDER).orElseThrow();
        if (!isAccepted(best, forms.size())) {
            log.trace("Name forms {} keep their spellings: no nominative accepted", forms);
            return forms.stream().map(this::tally).toList();
        }
        final List<String> others =
                forms.stream().filter(form -> !best.forms.contains(form)).toList();
        final List<NameCandidate> found = new ArrayList<>();
        if (best.forms.size() == 1 && best.attested) {
            found.add(tally(best.display));
        } else {
            found.addAll(merged(best));
        }
        if (!others.isEmpty()) {
            found.addAll(resolve(others));
        }
        return found;
    }

    void logSummary(final int before, final int after) {
        log.debug(
                "Name forms grouped language={} groups={} formsMerged={} inferred={} competitors={} candidates={}->{}",
                language,
                groups,
                formsMerged,
                inferred,
                competitors,
                before,
                after);
        clause.logSummary();
    }

    private Map<String, Lemma> lemmasOf(final List<String> forms) {
        final Map<String, Lemma> lemmas = new LinkedHashMap<>();
        for (int at = 0; at < forms.size(); at++) {
            final String form = forms.get(at);
            final int first = at;
            for (final NameForms.Reading reading : readingsOf(form)) {
                final String key = Occurrences.keyOf(reading.lemma());
                final Lemma lemma = lemmas.computeIfAbsent(key, _ -> new Lemma(key, reading.lemma(), first));
                if (lemma.forms.add(form)) {
                    lemma.count += tally(form).count();
                }
                if (Occurrences.keyOf(form).equals(key)) {
                    lemma.attested = true;
                    lemma.display = form;
                }
            }
        }
        return lemmas;
    }

    private boolean isAccepted(final Lemma best, final int groupSize) {
        if (titles.contains(best.key)) {
            log.debug("Name forms not grouped under a title of the language");
            return false;
        }
        return best.forms.size() >= 2 || (best.attested && groupSize == 1);
    }

    private List<NameCandidate> merged(final Lemma best) {
        final List<NameCandidate> found = new ArrayList<>();
        final List<String> aliases = new ArrayList<>();
        int decidedAway = 0;
        for (final String form : best.forms) {
            final Optional<Competitor> competitor = competitorOf(form, best.key);
            if (competitor.isPresent()) {
                found.add(competitor.get().candidate());
                decidedAway += competitor.get().decided();
            } else {
                aliases.addAll(form.equals(best.display) ? List.of() : List.of(form));
                aliases.addAll(tally(form).aliases());
            }
        }
        final NameCandidate first = tally(best.forms.iterator().next());
        found.addFirst(new NameCandidate(
                best.display, best.count - decidedAway, first.firstSentence(), aliases, false, !best.attested));
        groups++;
        formsMerged += best.forms.size();
        inferred += best.attested ? 0 : 1;
        log.trace("Name forms {} grouped under {}, inferred {}", best.forms, best.display, !best.attested);
        return found;
    }

    private record Competitor(NameCandidate candidate, int decided) {}

    // A form that is the gendered nominative of another name is that name's candidate too once the clause decides
    // one of its mentions so; it keeps its unsure mentions, which count for both.
    private Optional<Competitor> competitorOf(final String form, final String lemmaKey) {
        final String formKey = Occurrences.keyOf(form);
        final Optional<NameForms.Reading> own = readingsOf(form).stream()
                .filter(reading -> reading.nominative() && reading.gender() != Gender.UNKNOWN)
                .filter(reading -> Occurrences.keyOf(reading.lemma()).equals(formKey))
                .filter(reading -> !formKey.equals(lemmaKey))
                .findFirst();
        if (own.isEmpty()) {
            return Optional.empty();
        }
        final Votes votes = votes(form, own.get());
        if (votes.forIt() == 0) {
            return Optional.empty();
        }
        competitors++;
        final NameCandidate tally = tally(form);
        log.debug("Name form decided for a name of its own in {} mentions, {} unsure", votes.forIt(), votes.unsure());
        return Optional.of(new Competitor(
                new NameCandidate(
                        form,
                        Math.min(tally.count(), votes.forIt() + votes.unsure()),
                        tally.firstSentence(),
                        tally.aliases()),
                Math.min(tally.count(), votes.forIt())));
    }

    private record Votes(int forIt, int unsure) {}

    private Votes votes(final String form, final NameForms.Reading own) {
        int forIt = 0;
        int unsure = 0;
        for (final Occurrences.Read segment : read) {
            for (final Occurrences.Word word : segment.words()) {
                if (word.text().equals(form)) {
                    final Optional<NameForms.Reading> decided =
                            clause.decide(segment.text(), word.start(), word.start() + form.length(), readingsOf(form));
                    forIt += decided.filter(own::equals).isPresent() ? 1 : 0;
                    unsure += decided.isEmpty() ? 1 : 0;
                }
            }
        }
        return new Votes(forIt, unsure);
    }

    private Set<NameForms.Reading> readingsOf(final String form) {
        return Objects.requireNonNull(readings.get(form), form);
    }

    private NameCandidate tally(final String form) {
        return Objects.requireNonNull(tallies.get(form), form);
    }
}
