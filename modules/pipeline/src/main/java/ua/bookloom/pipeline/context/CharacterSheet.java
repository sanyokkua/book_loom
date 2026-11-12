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
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.glossary.GlossaryNames;
import ua.bookloom.pipeline.glossary.NameClusters;
import ua.bookloom.pipeline.glossary.PronounGender;

/**
 * The character gender sheet the check against the neighbours and the name checks read: one
 * {@code name — gender} line for each glossary character the text names whose gender the person set or the book
 * proved, so a model never has to guess a pronoun or a verb ending from a name alone; a character whose gender only a rule or the model's guess gave is listed by its name alone. A character
 * the segment does not name is never listed, so the sheet stays as short as the scene. A short name that is part of a
 * character's full name ("Danny" of "Danny Vosk", by {@link NameClusters}), and has no gender of its own or the same
 * one, shares that character's line (15i.N2), and the pronouns the scene shows are listed only when they agree with the
 * set gender (15h.A2).
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CharacterSheet {

    /**
     * The sheet for some segments.
     *
     * @param segments the segments whose text decides who is present
     * @param glossary every glossary entry as the run read it
     * @param sourceLanguage the tag of the language the code reads the book in, which picks the pronouns read as
     *     evidence; null reads no pronoun, so the code gives no gender evidence
     * @return one line per present character of known gender, in glossary order, with the pronouns the text shows
     *     after the name when they agree with its gender ({@code Lyra — female (she ×2)}), a short name folded into its
     *     full name's line ({@code Danny Vosk (also Danny) — male}), and a gender only a rule or the model's guess gave
     *     left out ({@code Lyra}), because the checks hold the text to every gender a line states (design D5); never
     *     null, empty when none is present
     */
    public static List<String> select(
            final List<Segment> segments, final List<GlossaryEntry> glossary, @Nullable final String sourceLanguage) {
        return selectIn(Tokens.visibleTexts(segments), glossary, sourceLanguage);
    }

    /**
     * The sheet for some texts, for a caller whose texts are not segments (a source and its candidate).
     *
     * @param texts the texts whose names decide who is present, tokens already removed
     * @param glossary every glossary entry as the run read it
     * @param sourceLanguage as for {@link #select(List, List, String)}
     * @return the sheet's lines, as {@link #select(List, List, String)} builds them; never null
     */
    public static List<String> selectIn(
            final List<String> texts, final List<GlossaryEntry> glossary, @Nullable final String sourceLanguage) {
        final List<GlossaryEntry> characters = glossary.stream()
                .filter(entry -> entry.type() == TermType.CHARACTER)
                .filter(entry -> !entry.term().isBlank())
                .toList();
        final NameClusters clusters = NameClusters.of(glossary, null, sourceLanguage);
        final Map<GlossaryEntry, List<GlossaryEntry>> lines = new LinkedHashMap<>();
        for (final GlossaryEntry entry : characters) {
            if (namedIn(entry, texts)) {
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
        if (!isStated(full)) {
            log.debug(
                    "Character sheet: entry {} is listed by name only, its gender origin {}",
                    full.id(),
                    full.genderOrigin());
            return name;
        }
        final String line = name + " — " + full.gender().name().toLowerCase(Locale.ROOT);
        PronounGender.Evidence evidence = PronounGender.of(full, texts, sourceLanguage);
        for (final GlossaryEntry shorter : named) {
            if (!shorter.equals(full)) {
                evidence = evidence.and(PronounGender.of(shorter, texts, sourceLanguage));
            }
        }
        final String agreeing = evidence.compactAgreeing(full.gender());
        return agreeing.isEmpty() ? line : line + " (" + agreeing + ")";
    }

    // The one switch point for a gender on the sheet: the checks read every gender a line states as one to hold the
    // text to, so only the person's and the book's are stated; a rule's or a likely gender lists the name alone (a
    // rule's still reaches the model on the glossary line). Arm 4b (task 7.8) kept the name alone: a "(likely)" hint
    // brought no fewer gender-agreement errors (docs/evaluation/results-redesign-arms.md#redesign-arms).
    private static boolean isStated(final GlossaryEntry entry) {
        final Origin origin = entry.genderOrigin();
        return origin != null && origin.isEnforced();
    }

    // The lemma or any form the book writes names the character (a window naming only Велемира names Велемир).
    private static boolean namedIn(final GlossaryEntry entry, final List<String> texts) {
        return texts.stream().anyMatch(text -> GlossaryNames.isNamedIn(entry, text));
    }
}
