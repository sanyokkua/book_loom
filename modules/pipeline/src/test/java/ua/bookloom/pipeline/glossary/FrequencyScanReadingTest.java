package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.inject.Guice;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;

/**
 * A capitalised word the book also writes in lower case is a common word to a scan no reading follows, but a scan the
 * names reading follows keeps it for the reading once the book writes it with a capital mid-sentence three times or
 * more (15i.N5), and adds it only where the dial's plan reads it. The books are invented.
 */
class FrequencyScanReadingTest {

    private static final String PROJECT = "p1";
    private static final String LANGUAGE = "en";
    private static final String BRIDGE = "We crossed the Bridge.";
    private static final String BRIDGE_FELL = "The bridge fell.";

    private static List<String> lines(final List<String> first, final String... more) {
        final List<String> all = new ArrayList<>(first);
        all.addAll(List.of(more));
        return all;
    }

    private static List<String> copies(final String line, final int times) {
        return Collections.nCopies(times, line);
    }

    private static List<NameCandidate> scan(final List<String> book, final boolean forReading) {
        final BookSlice story = GlossaryTestSegments.of(book);
        return FrequencyScan.candidates(
                story.index(), story.ordinals(), FrequencyScan.PROPOSAL_MIN_COUNT, LANGUAGE, forReading);
    }

    // IF a scan with no reading after it kept "Bridge", THEN the run's own rescans would add a common word unread.
    @ParameterizedTest(name = "for reading {0}")
    @CsvSource({"false,0", "true,1"})
    void candidates_fourCapitalsMidSentenceAndOneLowerCase_areKeptOnlyForAReading(
            final boolean forReading, final int kept) {
        final List<NameCandidate> found = scan(lines(copies(BRIDGE, 4), BRIDGE_FELL), forReading);

        assertThat(found).hasSize(kept);
    }

    @Test
    void candidates_forReading_markTheCommonWordAsTheReadingsToDecide() {
        final List<NameCandidate> found = scan(lines(copies(BRIDGE, 4), BRIDGE_FELL), true);

        assertThat(found)
                .extracting(NameCandidate::term, NameCandidate::readingOnly)
                .containsExactly(tuple("Bridge", true));
    }

    // Two capitals mid-sentence (and one credited sentence start) are too few: the lower-case share still drops it.
    @Test
    void candidates_forReadingWithTwoCapitalsMidSentence_dropTheCommonWord() {
        final List<NameCandidate> found = scan(lines(copies(BRIDGE, 2), "Bridge stood again.", BRIDGE_FELL), true);

        assertThat(found).isEmpty();
    }

    // A name the book never writes in lower case is no reading's business: it is proposed as before, unmarked.
    @Test
    void candidates_forReadingAndANameNeverInLowerCase_isNotMarked() {
        final List<NameCandidate> found = scan(copies("We waited for Hale.", 3), true);

        assertThat(found)
                .extracting(NameCandidate::term, NameCandidate::readingOnly)
                .containsExactly(tuple("Hale", false));
    }

    // The bartender: five "Mark" mid-sentence, two "a mark" in lower case. Fast reads only a likely character, so the
    // word stays only when its pronouns made it a person; Balanced reads every name written three times; with no dial
    // (no reading follows) it is dropped as today.
    @ParameterizedTest(name = "{0} / dial {1} -> kept {2}")
    @CsvSource(
            value = {
                "Then Mark wiped the bar. He was tired.,FAST,true",
                "Then Mark wiped the bar.,FAST,false",
                "Then Mark wiped the bar.,BALANCED,true",
                "Then Mark wiped the bar. He was tired.,NONE,false"
            },
            nullValues = "NONE")
    void seededFor_bartenderMark_keepsTheWordWhereTheDialsPlanReadsIt(
            final String line, @Nullable final QualityDial dial, final boolean kept) {
        final BookSlice story = GlossaryTestSegments.of(
                lines(copies(line, 5), "There was a mark on the wall.", "Nobody knew who made the mark."));
        final GlossaryRepository glossary = Guice.createInjector(new DocumentModule(), new InMemoryPersistenceModule())
                .getInstance(GlossaryRepository.class);
        final ScanProposals proposals = Objects.requireNonNull(
                FrequencyScan.proposals(PROJECT, story.index(), story.ordinals(), LANGUAGE, glossary)
                        .data());

        final List<String> added = proposals.seededFor(story, LANGUAGE, dial).stream()
                .map(GlossaryEntry::term)
                .toList();

        assertThat(proposals.readingOnly()).containsExactly("Mark");
        assertThat(added.contains("Mark")).isEqualTo(kept);
    }
}
