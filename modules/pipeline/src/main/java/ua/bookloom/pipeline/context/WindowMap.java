package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.SnapshotRendering;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.glossary.NameForms;
import ua.bookloom.pipeline.lexicon.TermMatch;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.TermFacts;

/**
 * Chooses the map lines of one call from its window alone (design D8): an entry is sent only when its base form, a
 * recorded form or — on the target side of a proofread — a form its target generates occurs in the window, so a draft
 * and its repair see the same names and nothing from elsewhere in the book. A large glossary would drown a short
 * passage, and a name that is not in the passage invites the model to put it there.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WindowMap {

    // Texts are joined with a blank line, so no name runs from one segment into the next.
    private static final String JOIN = "\n\n";
    private static final String GLOSS = "; gloss once: ";

    /** One chosen line with where the window first names its entry and how often. */
    private record Placed(int at, String line, boolean gloss, int occurrences, boolean kept) {}

    /** The glossary's share of one call's lines while they are chosen; used inside {@link #select} only. */
    private record Glossary(List<Placed> lines, Set<String> locked, List<Placed> suggested) {
        long glossed() {
            return Stream.concat(lines.stream(), suggested.stream())
                    .filter(Placed::gloss)
                    .count();
        }
    }

    /** What one call reads its names from. */
    private record Texts(
            String sources, String targets, @Nullable String targetLanguage) {}

    /**
     * The map lines of one call.
     *
     * @param window the non-null window whose source text decides what is present
     * @param targets the non-null current translations of the focus a proofread also reads; empty for a draft or a
     *     repair
     * @param map the non-null book map
     * @param masks the non-null masks of the focus items, whose tokens a locked entry is listed by
     * @param filter the non-null filter that keeps function words and declined forms out of the recurring terms
     * @param targetLanguage the target language tag the target forms are generated in, or null when unknown
     * @return the lines; never null, {@link MapLines#NONE}-equal when the window names nothing on the map
     */
    public static MapLines select(
            final Window window,
            final List<String> targets,
            final BookMap map,
            final List<ProtectedMask> masks,
            final LexiconFilter filter,
            @Nullable final String targetLanguage) {
        return MapLines.of(selectCounted(window, targets, map, masks, filter, targetLanguage));
    }

    /**
     * The map lines of one call with how often the window names each and whether it may be dropped, read as
     * {@link #select} reads them.
     *
     * @param window the non-null window whose source text decides what is present
     * @param targets the non-null current translations of the focus a proofread also reads; empty otherwise
     * @param map the non-null book map
     * @param masks the non-null masks of the focus items, whose tokens a locked entry is listed by
     * @param filter the non-null filter that keeps function words and declined forms out of the recurring terms
     * @param targetLanguage the target language tag the target forms are generated in, or null when unknown
     * @return the entries, the glossary's first, then the locked, the suggested and the recurring terms', each in the
     *     order the window names them; never null, empty when the window names nothing on the map
     */
    public static List<MapEntry> selectCounted(
            final Window window,
            final List<String> targets,
            final BookMap map,
            final List<ProtectedMask> masks,
            final LexiconFilter filter,
            @Nullable final String targetLanguage) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(masks, "masks");
        Objects.requireNonNull(filter, "filter");
        final Texts texts = new Texts(
                String.join(JOIN, window.visibleSources()),
                String.join(
                        JOIN,
                        targets.stream().map(text -> Tokens.replace(text, " ")).toList()),
                targetLanguage);
        final Glossary glossary = glossary(window, map, masks, texts);
        final List<MapEntry> entries = new ArrayList<>(ordered(MapEntry.Section.GLOSSARY, glossary.lines()));
        glossary.locked().forEach(line -> entries.add(new MapEntry(MapEntry.Section.LOCKED, line, 1, true)));
        entries.addAll(ordered(MapEntry.Section.SUGGESTED, glossary.suggested()));
        entries.addAll(ordered(MapEntry.Section.LEXICON, lexicon(map, texts.sources(), filter)));
        logLines(MapLines.of(entries), map, glossary.glossed());
        return List.copyOf(entries);
    }

    private static Glossary glossary(
            final Window window, final BookMap map, final List<ProtectedMask> masks, final Texts texts) {
        final Glossary glossary = new Glossary(new ArrayList<>(), new LinkedHashSet<>(), new ArrayList<>());
        for (final GlossaryEntry entry : map.entries()) {
            final SnapshotTerm term = InjectedTerms.snapshotOf(entry);
            if (entry.locked() && InjectedTerms.hasTarget(term)) {
                glossary.locked().addAll(lockedLines(term, masks));
                continue;
            }
            final int at = firstAt(entry, texts);
            if (at < 0) {
                log.trace("Window map left out {}: not named in the window", entry.term());
                continue;
            }
            final boolean gloss = glossesHere(entry, map, window);
            final int occurrences = Math.max(1, TermMatch.namedCount(entry.names(), texts.sources()));
            // A pinned name, and a character whose gender the line states, are never dropped to fit the window.
            final boolean kept = !entry.isSuggested() || statesGender(entry);
            (entry.isSuggested() ? glossary.suggested() : glossary.lines())
                    .add(new Placed(at, lineOf(entry, gloss), gloss, occurrences, kept));
        }
        return glossary;
    }

    private static List<String> lockedLines(final SnapshotTerm term, final List<ProtectedMask> masks) {
        final List<String> lines = masks.stream()
                .flatMap(mask -> InjectedTerms.lines(term, mask).stream())
                .distinct()
                .toList();
        if (lines.isEmpty()) {
            log.trace("Window map left out locked {}: its token is not in the window", term.term());
        }
        return lines;
    }

    // The source side by the entry's names; a proofread's target side by the forms its target generates.
    private static int firstAt(final GlossaryEntry entry, final Texts texts) {
        final int inSource = earliest(entry.names(), texts.sources());
        if (inSource >= 0 || texts.targets().isEmpty() || !InjectedTerms.hasTarget(InjectedTerms.snapshotOf(entry))) {
            return inSource;
        }
        final String target = Objects.requireNonNull(entry.target(), "target");
        final int inTarget = earliest(
                NameForms.forms(target, entry.gender(), texts.targetLanguage()).all(), texts.targets());
        return inTarget < 0 ? -1 : texts.sources().length() + JOIN.length() + inTarget;
    }

    private static int earliest(final Iterable<String> names, final String text) {
        int earliest = -1;
        for (final String name : names) {
            final int at = TermMatch.namedAt(name, text);
            if (at >= 0 && (earliest < 0 || at < earliest)) {
                earliest = at;
            }
        }
        return earliest;
    }

    // The gloss goes out once: in the window whose focus holds the book's first segment naming the entry.
    private static boolean glossesHere(final GlossaryEntry entry, final BookMap map, final Window window) {
        final @Nullable String firstUse = map.glossFirstUse().get(entry.id());
        return BookMap.isGlossed(entry)
                && firstUse != null
                && window.focus().segmentIds().contains(firstUse);
    }

    private static boolean statesGender(final GlossaryEntry entry) {
        return entry.type() == TermType.CHARACTER
                && entry.gender() != Gender.UNKNOWN
                && entry.genderOrigin() != Origin.LIKELY;
    }

    private static String lineOf(final GlossaryEntry entry, final boolean gloss) {
        final String facts = TermFacts.of(entry.type(), entry.gender(), entry.genderOrigin());
        return InjectedTerms.line(entry.term(), entry.target(), gloss ? facts + GLOSS + entry.meaning() : facts);
    }

    private static List<Placed> lexicon(final BookMap map, final String sources, final LexiconFilter filter) {
        final List<Placed> placed = new ArrayList<>();
        for (final LexiconEntry entry : map.terms()) {
            final List<String> forms = Stream.concat(Stream.of(entry.term()), entry.forms().stream())
                    .toList();
            final int at = earliest(forms, sources);
            if (at < 0) {
                log.trace("Window map left out term {}: not named in the window", entry.term());
                continue;
            }
            if (InjectedLexicon.heldByGlossary(entry, map.entries())) {
                log.trace("Window map left out term {}: the glossary holds it", entry.term());
                continue;
            }
            final Optional<SnapshotRendering> rendering = InjectedLexicon.renderingOf(entry, filter);
            final int occurrences = Math.max(1, TermMatch.namedCount(forms, sources));
            rendering.ifPresent(
                    found -> placed.add(new Placed(at, InjectedLexicon.line(found), false, occurrences, false)));
        }
        return placed;
    }

    private static List<MapEntry> ordered(final MapEntry.Section section, final List<Placed> placed) {
        return placed.stream()
                .sorted(Comparator.comparingInt(Placed::at))
                .map(found -> new MapEntry(section, found.line(), found.occurrences(), found.kept()))
                .toList();
    }

    private static void logLines(final MapLines lines, final BookMap map, final long glossed) {
        log.debug(
                "Window map glossary={}/{} suggested={} lexicon={}/{} locked={} gloss={}",
                lines.glossary().size(),
                map.entries().size(),
                lines.suggested().size(),
                lines.lexicon().size(),
                map.terms().size(),
                lines.locked().size(),
                glossed);
        if (log.isTraceEnabled()) {
            Stream.of(lines.glossary(), lines.locked(), lines.suggested(), lines.lexicon())
                    .flatMap(List::stream)
                    .forEach(line -> log.trace("Window map line {}", line));
        }
    }
}
