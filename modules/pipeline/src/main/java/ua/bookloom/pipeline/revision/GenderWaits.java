package ua.bookloom.pipeline.revision;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.GenderWait;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * The open gender deferrals read as what the person has to do: confirm a gender the glossary only suggests, or set
 * one it lacks (the narrator's own deferrals count as the second).
 *
 * @param characters one entry per waiting character, most waiting first; never null
 * @param suggestedSegments the distinct segments that wait on a suggested gender
 * @param unknownSegments the distinct segments that wait on no gender, or on the narrator's
 */
@Slf4j
record GenderWaits(List<GenderWait> characters, int suggestedSegments, int unknownSegments) {

    GenderWaits {
        characters = List.copyOf(Objects.requireNonNull(characters, "characters"));
    }

    // A segment naming two waiting characters is one waiting segment in the totals, yet counts for each character,
    // because setting either one is a reason to look at it. A character with only a suggested gender is a suggestion
    // to confirm; one the glossary does not hold, or holds with none, is unknown.
    static GenderWaits of(final List<Deferral> open, final List<GlossaryEntry> glossary) {
        log.debug("Gender waits read from {} open deferrals and {} glossary entries", open.size(), glossary.size());
        final Set<String> suggestedTerms = new HashSet<>();
        glossary.stream().filter(GlossaryEntry::isGenderSuggested).forEach(entry -> suggestedTerms.add(entry.term()));
        final Map<String, Set<String>> byCharacter = new HashMap<>();
        final Set<String> suggestedSegments = new HashSet<>();
        final Set<String> unknownSegments = new HashSet<>();
        open.stream()
                .filter(deferral -> deferral.reason() == DeferralReason.GENDER_UNKNOWN)
                .forEach(deferral -> sort(deferral, suggestedTerms, byCharacter, suggestedSegments, unknownSegments));
        final List<GenderWait> characters = rank(byCharacter, suggestedTerms);
        log.debug(
                "Gender waits: {} characters, {} segments wait on a suggested gender, {} on none",
                characters.size(),
                suggestedSegments.size(),
                unknownSegments.size());
        return new GenderWaits(characters, suggestedSegments.size(), unknownSegments.size());
    }

    private static void sort(
            final Deferral deferral,
            final Set<String> suggestedTerms,
            final Map<String, Set<String>> byCharacter,
            final Set<String> suggestedSegments,
            final Set<String> unknownSegments) {
        final String waitingOn = deferral.waitingOn();
        final boolean character = waitingOn != null && !DeferralRegister.NARRATOR.equals(waitingOn);
        if (character) {
            byCharacter.computeIfAbsent(waitingOn, name -> new HashSet<>()).add(deferral.segmentId());
        }
        final boolean suggested = character && suggestedTerms.contains(waitingOn);
        (suggested ? suggestedSegments : unknownSegments).add(deferral.segmentId());
    }

    private static List<GenderWait> rank(final Map<String, Set<String>> byCharacter, final Set<String> suggestedTerms) {
        return byCharacter.entrySet().stream()
                .map(entry -> new GenderWait(
                        entry.getKey(), entry.getValue().size(), suggestedTerms.contains(entry.getKey())))
                .sorted(Comparator.comparingInt(GenderWait::segments).reversed().thenComparing(GenderWait::character))
                .toList();
    }
}
