package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.glossary.NameClusters;
import ua.bookloom.pipeline.glossary.PronounGender;
import ua.bookloom.pipeline.lexicon.TermMatch;

/**
 * The character gender sheet of a segment: one {@code name — gender} line for each glossary character the segment
 * names whose gender is known, so a model never has to guess a pronoun or a verb ending from a name alone. A character
 * the segment does not name is never listed, so the sheet stays as short as the scene. A short name that is part of a
 * character's full name ("Danny" of "Danny Vosk", by {@link NameClusters}), and has no gender of its own or the same
 * one, shares that character's line (15i.N2), and the pronouns the scene shows are listed only when they agree with the
 * set gender (15h.A2).
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InjectedCharacters {

    /**
     * The sheet for some segments.
     *
     * @param segments the segments whose text decides who is present
     * @param glossary every glossary entry as the run read it
     * @param sourceLanguage the tag of the language the code reads the book in, which picks the pronouns read as
     *     evidence; null reads no pronoun, so the code gives no gender evidence
     * @return one line per present character of known gender, in glossary order, with the pronouns the text shows
     *     after the name when they agree with its gender ({@code Lyra — female (she ×2)}), a short name folded into its
     *     full name's line ({@code Danny Vosk (also Danny) — male}); never null, empty when none is present
     */
    public static List<String> select(
            final List<Segment> segments, final List<GlossaryEntry> glossary, @Nullable final String sourceLanguage) {
        final List<String> texts = Tokens.visibleTexts(segments);
        final List<GlossaryEntry> characters = glossary.stream()
                .filter(entry -> entry.type() == TermType.CHARACTER)
                .filter(entry -> !entry.term().isBlank())
                .toList();
        final NameClusters clusters = NameClusters.of(glossary, null, sourceLanguage);
        final Map<GlossaryEntry, List<GlossaryEntry>> lines = new LinkedHashMap<>();
        for (final GlossaryEntry entry : characters) {
            if (namedIn(entry.term(), texts)) {
                headOf(entry, clusters)
                        .ifPresent(head -> lines.computeIfAbsent(head, _ -> new ArrayList<>())
                                .add(entry));
            }
        }
        final List<String> sheet = characters.stream()
                .filter(entry -> entry.gender() != Gender.UNKNOWN)
                .filter(lines::containsKey)
                .map(entry -> lineOf(entry, Objects.requireNonNull(lines.get(entry), "named"), texts, sourceLanguage))
                .toList();
        log.trace("Character sheet offered={} selected={}", glossary.size(), sheet.size());
        return sheet;
    }

    /**
     * The sheet for some segments, without pronoun evidence.
     *
     * @param segments the segments whose text decides who is present
     * @param glossary every glossary entry as the run read it
     * @return one {@code name — gender} line per present character of known gender; never null
     */
    public static List<String> select(final List<Segment> segments, final List<GlossaryEntry> glossary) {
        return select(segments, glossary, null);
    }

    // The line a present name is listed on: its cluster's full name when that has a gender and this name has none or
    // the same one, else the name itself; nothing when the line's gender is not known.
    private static Optional<GlossaryEntry> headOf(final GlossaryEntry entry, final NameClusters clusters) {
        final GlossaryEntry head = clusters.fullNameOf(entry)
                .filter(full -> full.gender() != Gender.UNKNOWN)
                .filter(full -> entry.gender() == Gender.UNKNOWN || entry.gender() == full.gender())
                .orElse(entry);
        log.debug("Character sheet: entry {} is listed under {}", entry.id(), head.id());
        return Optional.of(head).filter(listed -> listed.gender() != Gender.UNKNOWN);
    }

    private static String lineOf(
            final GlossaryEntry full,
            final List<GlossaryEntry> named,
            final List<String> texts,
            @Nullable final String sourceLanguage) {
        final List<String> shortNames = named.stream()
                .filter(entry -> !entry.equals(full))
                .map(GlossaryEntry::term)
                .toList();
        final String name =
                shortNames.isEmpty() ? full.term() : full.term() + " (also " + String.join(", ", shortNames) + ")";
        final String line = name + " — " + full.gender().name().toLowerCase(Locale.ROOT);
        PronounGender.Evidence evidence = PronounGender.of(full.term(), texts, sourceLanguage);
        for (final String shortName : shortNames) {
            evidence = evidence.and(PronounGender.of(shortName, texts, sourceLanguage));
        }
        final String agreeing = evidence.compactAgreeing(full.gender());
        return agreeing.isEmpty() ? line : line + " (" + agreeing + ")";
    }

    /**
     * The lines that fit a token allowance, from the first, so a long cast is cut at the end and never half-shown.
     *
     * @param lines the sheet's lines
     * @param allowance the tokens the sheet may take, from {@link ContextBudget#characterAllowance(int)}
     * @return the leading lines whose estimated size stays within {@code allowance}
     */
    public static List<String> within(final List<String> lines, final int allowance) {
        final List<String> kept = new ArrayList<>();
        int tokens = 0;
        for (final String line : lines) {
            tokens += TokenEstimator.estimate(line, null);
            if (tokens > allowance) {
                break;
            }
            kept.add(line);
        }
        return kept;
    }

    private static boolean namedIn(final String term, final List<String> texts) {
        return texts.stream().anyMatch(text -> TermMatch.isNamedIn(term, text));
    }
}
