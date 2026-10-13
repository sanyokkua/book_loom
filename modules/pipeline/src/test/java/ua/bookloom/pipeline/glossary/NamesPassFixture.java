package ua.bookloom.pipeline.glossary;

import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.NamesPass;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.PipelineModule;
import ua.bookloom.pipeline.ReviewModeTestModule;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * The invented book and the stored tables the names pass tests start from. The book has sixty-odd one-line
 * paragraphs: a man (Brann) and a woman (Wren) each followed by a masculine pronoun, a place, a brand, a speaking
 * place, a word that is no name, a locked office, a street and five recurring common words. Nothing in it is taken
 * from a real book.
 */
final class NamesPassFixture {

    static final String HE = " He did not pause.";

    /** A clock only the test moves. */
    static final class StepClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(final Duration step) {
            now = now.plus(step);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    final StepClock clock = new StepClock();
    final Injector injector = Guice.createInjector(
            new DocumentModule(),
            new PersistenceModule(),
            Modules.override(new PipelineModule())
                    .with(binder -> binder.bind(Clock.class).toInstance(clock)),
            new ReviewModeTestModule());
    final GlossaryRepository glossary = injector.getInstance(GlossaryRepository.class);
    final LexiconRepository lexicon = injector.getInstance(LexiconRepository.class);

    NamesPass pass() {
        return injector.getInstance(NamesPass.class);
    }

    /** Stores the project with the brief and opens the invented book. */
    NamesPassFixture open(final BookBrief brief) {
        injector.getInstance(ProjectRepository.class).save(GlossaryServiceFixtures.project(brief));
        injector.getInstance(OpenProjects.class).put(PROJECT, GlossaryServiceFixtures.book(body(), "Title"));
        return this;
    }

    static BookBrief brief(final String target, final NamePolicy names) {
        final BookBrief base = GlossaryServiceFixtures.withTarget(BookBrief.defaults("en"), target);
        return new BookBrief(
                base.sourceLanguage(),
                base.targetLanguage(),
                base.genre(),
                base.register(),
                base.voiceEra(),
                base.audience(),
                names,
                base.foreignPassages(),
                base.footnotes(),
                base.units(),
                base.balance(),
                base.alsoTranslate(),
                base.dial());
    }

    static GlossaryEntry untyped(final String term) {
        return new GlossaryEntry(
                GlossaryIds.of(PROJECT, term), PROJECT, term, null, TermType.OTHER, Gender.UNKNOWN, false);
    }

    /** The names a scan or a person would have put in the glossary, none of them typed or targeted yet. */
    NamesPassFixture seedOpenNames() {
        Stream.of("Brann", "Harrowgate", "Zorbo", "Silverbrook", "Overture")
                .forEach(term -> glossary.add(untyped(term)));
        return this;
    }

    /** The person's own work: a woman's gender, a locked office and a street with a target. */
    NamesPassFixture seedPersonsEntries() {
        glossary.add(new GlossaryEntry(
                GlossaryIds.of(PROJECT, "Wren"), PROJECT, "Wren", null, TermType.CHARACTER, Gender.FEMALE, false));
        glossary.add(new GlossaryEntry(
                GlossaryIds.of(PROJECT, "Gatewarden"),
                PROJECT,
                "Gatewarden",
                "Брамник",
                TermType.TERM,
                Gender.UNKNOWN,
                true));
        glossary.add(new GlossaryEntry(
                GlossaryIds.of(PROJECT, "Marrow Lane"),
                PROJECT,
                "Marrow Lane",
                "вулиця Марроу",
                TermType.PLACE,
                Gender.UNKNOWN,
                false));
        return this;
    }

    /** A recurring word to be dropped, and one the person gave a rendering. */
    NamesPassFixture seedTerms() {
        lexicon.put(LexiconEntry.of(PROJECT, "ferry"));
        lexicon.put(LexiconEntry.of(PROJECT, "anvil").withChosen("ковадло"));
        return this;
    }

    List<GlossaryEntry> glossaryNow() {
        return Objects.requireNonNull(glossary.all(PROJECT).data());
    }

    List<LexiconEntry> lexiconNow() {
        return Objects.requireNonNull(lexicon.all(PROJECT).data());
    }

    static List<String> body() {
        final List<String> lines = new ArrayList<>();
        lines.addAll(peopleLines());
        lines.addAll(placeAndThingLines());
        lines.addAll(officeAndStreetLines());
        Stream.of("beacon", "ferry", "wardstone", "anvil", "gantry").forEach(word -> lines.addAll(wordLines(word)));
        return List.copyOf(lines);
    }

    private static List<String> peopleLines() {
        final List<String> lines = new ArrayList<>();
        lines.addAll(personLines(
                "Brann",
                List.of(
                        "climbed the hill",
                        "crossed the field",
                        "entered the inn",
                        "studied the sky",
                        "followed the river",
                        "reached the gate")));
        lines.addAll(personLines(
                "Wren",
                List.of(
                        "polished the saddle",
                        "mended the fence",
                        "lit the stove",
                        "counted the sacks",
                        "swept the yard",
                        "fed the geese")));
        return lines;
    }

    private static List<String> placeAndThingLines() {
        final List<String> lines = new ArrayList<>();
        lines.addAll(sentences(
                "So they reached %s at dusk.",
                "And rain fell over %s all night.",
                "So a bell rang in %s.",
                "And nobody left %s before spring.",
                "Harrowgate"));
        lines.addAll(sentences(
                "So a bottle of %s stood on the shelf.",
                "And a crate of %s waited by the door.",
                "So a poster for %s hung in the window.",
                "And an empty tin of %s rolled away.",
                "Zorbo"));
        lines.addAll(sentences(
                "So they camped beside %s that week.",
                "And mist hid %s from the road.",
                "So a ford crossed %s below the mill.",
                "And frost covered %s by morning.",
                "Silverbrook"));
        return lines;
    }

    private static List<String> officeAndStreetLines() {
        final List<String> lines = new ArrayList<>();
        lines.add("So the Overture began.");
        lines.add("And the Overture ended.");
        lines.addAll(sentences(
                "So the %s nodded at the stranger.",
                "And the %s opened the bolt.", "So the %s shut the hatch.", "And the %s lit a lantern.", "Gatewarden"));
        lines.addAll(sentences(
                "So they walked down %s at noon.",
                "And a cart stood in %s.", "So a dog slept on %s.", "And the baker lived in %s.", "Marrow Lane"));
        return lines;
    }

    private static List<String> personLines(final String name, final List<String> deeds) {
        return deeds.stream().map(deed -> "So " + name + " " + deed + "." + HE).toList();
    }

    private static List<String> sentences(
            final String one, final String two, final String three, final String four, final String name) {
        return Stream.of(one, two, three, four)
                .map(template -> template.formatted(name))
                .toList();
    }

    private static List<String> wordLines(final String word) {
        final List<String> deeds = List.of("burned", "rattled", "creaked", "gleamed", "stood", "shook");
        return IntStream.range(0, deeds.size())
                .mapToObj(index -> "And the " + word + " " + deeds.get(index) + ".")
                .toList();
    }
}
