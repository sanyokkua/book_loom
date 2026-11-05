package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.lexicon.TermMatch;

/**
 * Block 8's recently-present card (design D10): the map characters and places named shortly before the window in the
 * same chapter but not in the window, most recent first, so a pronoun in the passage can be read against who was just
 * on stage, at no model call. Until group 12 brings the chapter rule, a chapter is the body unit the focus sits in.
 * Left out at a chapter start, where nothing of the chapter precedes the window.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RecentlyPresentCard {

    /** How many entries the card names at most: a few names, not a second glossary. */
    static final int CARD_ENTRIES = 5;

    /** How many paragraphs before the window the card looks back over. */
    static final int CARD_PARAGRAPHS = 12;

    // Texts are joined with a blank line, so no name runs from one segment into the next.
    private static final String JOIN = "\n\n";
    private static final String ENTRY_SEPARATOR = ", ";
    private static final String LIKELY = "LIKELY_";

    /** Where the window sits in its chapter. */
    private record Place(String unitId, int chapter, List<Integer> before) {}

    /** One candidate with the last paragraph naming it and its place on the map, for the order. */
    private record Seen(GlossaryEntry entry, int last, int mapOrder) {}

    /**
     * The card of one call.
     *
     * @param map the non-null book map whose characters and places the card may name
     * @param window the non-null window of the call
     * @param frame the non-null run frame, whose index places the window and whose brief says who narrates
     * @return the card's text; empty at a chapter start or when the focus is not in the story
     */
    static String of(final BookMap map, final Window window, final RunFrame frame) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(frame, "frame");
        final BlockWords words = BlockWords.bundled();
        final Optional<Place> place = placeOf(frame.index(), window);
        if (place.isEmpty()) {
            return "";
        }
        final Place found = place.get();
        final List<Integer> reach = found.before()
                .subList(
                        Math.max(0, found.before().size() - CARD_PARAGRAPHS),
                        found.before().size());
        final List<Seen> seen =
                present(map, frame.index().visibleTexts(reach), String.join(JOIN, window.visibleSources()));
        final List<String> lines = new ArrayList<>();
        lines.add(words.get(BlockWords.CARD_HEADING));
        lines.add(sceneLine(found, seen, words));
        narratorLine(frame, found.unitId(), words).ifPresent(lines::add);
        final String card = String.join("\n", lines);
        log.debug(
                "Recently-present card chapter={} after={} reach={} entries={}",
                found.chapter(),
                found.before().size(),
                reach.size(),
                seen.stream().map(candidate -> candidate.entry().term()).toList());
        log.trace("Recently-present card: {}", card);
        return card;
    }

    // The chapter the focus sits in and the story paragraphs of it before the window's first segment.
    private static Optional<Place> placeOf(final BookIndex index, final Window window) {
        final OptionalInt focus = window.focus().segmentIds().isEmpty()
                ? OptionalInt.empty()
                : index.ordinalOf(window.focus().segmentIds().getFirst());
        final List<Integer> story = index.story();
        if (focus.isEmpty() || !story.contains(focus.getAsInt())) {
            log.debug("Recently-present card left out: the focus is not in the story");
            return Optional.empty();
        }
        final int start = windowStart(index, window, focus.getAsInt());
        final String unitId = index.segment(focus.getAsInt()).unitId();
        final List<Integer> before = story.stream()
                .filter(ordinal ->
                        ordinal < start && index.segment(ordinal).unitId().equals(unitId))
                .toList();
        if (before.isEmpty()) {
            log.debug("Recently-present card left out: the window starts chapter {}", unitId);
            return Optional.empty();
        }
        final List<String> units = story.stream()
                .map(ordinal -> index.segment(ordinal).unitId())
                .distinct()
                .toList();
        return Optional.of(new Place(unitId, units.indexOf(unitId) + 1, before));
    }

    // The earliest segment the window shows, previous pairs included.
    private static int windowStart(final BookIndex index, final Window window, final int focus) {
        return Stream.concat(
                        window.previous().stream().map(WindowPair::segmentId), window.focus().segmentIds().stream())
                .map(index::ordinalOf)
                .filter(OptionalInt::isPresent)
                .mapToInt(OptionalInt::getAsInt)
                .min()
                .orElse(focus);
    }

    private static boolean isCandidate(final GlossaryEntry entry, final String windowText) {
        if (entry.type() != TermType.CHARACTER && entry.type() != TermType.PLACE) {
            return false;
        }
        if (isNamed(entry, windowText)) {
            log.trace("Recently-present card left out {}: named in the window", entry.term());
            return false;
        }
        return true;
    }

    private static List<Seen> present(final BookMap map, final List<String> reach, final String windowText) {
        final List<Seen> seen = new ArrayList<>();
        for (int order = 0; order < map.entries().size(); order++) {
            final GlossaryEntry entry = map.entries().get(order);
            if (!isCandidate(entry, windowText)) {
                continue;
            }
            final int last = IntStream.range(0, reach.size())
                    .filter(paragraph -> isNamed(entry, reach.get(paragraph)))
                    .max()
                    .orElse(-1);
            if (last >= 0) {
                seen.add(new Seen(entry, last, order));
            }
        }
        return seen.stream()
                .sorted(Comparator.comparingInt(Seen::last).reversed().thenComparingInt(Seen::mapOrder))
                .limit(CARD_ENTRIES)
                .toList();
    }

    private static boolean isNamed(final GlossaryEntry entry, final String text) {
        return entry.names().stream().anyMatch(name -> TermMatch.isNamedIn(name, text));
    }

    private static String sceneLine(final Place place, final List<Seen> seen, final BlockWords words) {
        final String scene = words.get(BlockWords.CARD_SCENE)
                .replace("{chapter}", Integer.toString(place.chapter()))
                .replace("{paragraph}", Integer.toString(place.before().size()));
        if (seen.isEmpty()) {
            return scene;
        }
        final String entries = String.join(
                ENTRY_SEPARATOR,
                seen.stream()
                        .map(candidate -> entryOf(candidate.entry(), words))
                        .toList());
        return scene + " " + words.get(BlockWords.CARD_PRESENT).replace("{entries}", entries);
    }

    // A likely gender is told as likely, so the model reads it as a guess and is held to nothing.
    private static String entryOf(final GlossaryEntry entry, final BlockWords words) {
        final String facts;
        if (entry.type() == TermType.PLACE) {
            facts = words.get(BlockWords.CARD_TYPE + entry.type().name());
        } else if (entry.gender() == Gender.UNKNOWN) {
            facts = words.get(BlockWords.CARD_TYPE + TermType.CHARACTER.name());
        } else {
            final String gender =
                    words.get(BlockWords.CARD_GENDER + entry.gender().name());
            facts = entry.genderOrigin() == Origin.LIKELY
                    ? words.get(BlockWords.CARD_LIKELY).replace("{gender}", gender)
                    : gender;
        }
        return MapLines.line(entry.term(), entry.target(), facts);
    }

    // The style sheet states a narrator the brief names; the card names one only the chapter's own text shows.
    private static Optional<String> narratorLine(final RunFrame frame, final String unitId, final BlockWords words) {
        final Narrator narrator = frame.brief().narrator();
        if (narrator.person() != NarratorPerson.UNSPECIFIED) {
            return Optional.empty();
        }
        final boolean firstPerson = frame.firstPersonUnits().contains(unitId);
        log.debug("Recently-present card narrator of {}: first person {}", unitId, firstPerson);
        return firstPerson
                ? Optional.of(words.get(BlockWords.CARD_NARRATOR + narratorKey(narrator)))
                : Optional.empty();
    }

    private static String narratorKey(final Narrator narrator) {
        final @Nullable String gender =
                switch (narrator.gender()) {
                    case MALE, FEMALE -> narrator.gender().name();
                    case NEUTER, UNKNOWN -> null;
                };
        if (gender == null) {
            return Gender.UNKNOWN.name();
        }
        return narrator.hasSuggestedGender() ? LIKELY + gender : gender;
    }
}
