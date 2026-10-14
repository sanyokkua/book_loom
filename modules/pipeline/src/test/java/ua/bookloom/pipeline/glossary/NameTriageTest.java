package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.inject.Guice;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.IndexTestBooks;

/**
 * The code's triage of the name candidates before any model reads them (15i.N3): a candidate that is mostly a heading
 * or a title is dropped, a name written as a pair is one candidate, and a spelling that differs only in letter case is
 * an alias. It sits as the last step of {@link FrequencyScan#candidates}, so the glossary scan, the model scan and the
 * decided-so-far scan all see the triaged list, and it measures the whole book through the index whatever slice the
 * caller scans. A mention in narration is a mention in the story's running text (speech included: a name that is only
 * spoken is still a name); every other mention, in a heading, title line, contents entry or non-story unit, is not.
 */
class NameTriageTest {

    private static final String NARRATION = "Then we saw Quillon at the gate.";

    private static List<String> copies(final String line, final int times) {
        return Collections.nCopies(times, line);
    }

    private static Unit unit(final String id, final UnitRole role, final SegmentKind kind, final List<String> lines) {
        return IndexTestBooks.unit(id, role, kind, lines.toArray(String[]::new));
    }

    private static Unit story(final String id, final List<String> lines) {
        return unit(id, UnitRole.BODY, SegmentKind.PARAGRAPH, lines);
    }

    private static List<NameCandidate> candidates(final Document document, final int minCount) {
        final BookIndex index = BookIndex.of(document);
        return FrequencyScan.candidates(index, index.story(), minCount, "en");
    }

    private static List<NameCandidate> candidatesOfEverySegment(final Document document, final int minCount) {
        final BookIndex index = BookIndex.of(document);
        return FrequencyScan.candidates(
                index, IntStream.range(0, index.size()).boxed().toList(), minCount, "en");
    }

    private static List<String> terms(final List<NameCandidate> candidates) {
        return candidates.stream().map(NameCandidate::term).toList();
    }

    private static List<String> split(final String csv) {
        return csv.isBlank() ? List.of() : Arrays.asList(csv.split("\\|"));
    }

    // ----- headings, titles, contents and non-story units -----

    // IF 70 % or more of a name's mentions are headings and fewer than three are in the story, THEN it is dropped;
    // three in the story keep it however many headings repeat it.
    @ParameterizedTest(name = "{0} headings, {1} in the story -> [{2}]")
    @CsvSource({
        "8, 2, ''",
        "7, 2, ''",
        "20, 2, ''",
        "7, 3, Quillon",
        "6, 4, Quillon",
        "1, 2, Quillon",
        "0, 2, Quillon",
        "0, 3, Quillon"
    })
    void candidates_shareOfMentionsInHeadings_dropsOnlyWhenHighAndNarrationIsUnderThree(
            final int headings, final int narration, final String expected) {
        final List<Unit> units = new ArrayList<>();
        units.add(unit("h", UnitRole.BODY, SegmentKind.HEADING, copies("Quillon", headings)));
        units.add(story("p", copies(NARRATION, narration)));
        final Document book = IndexTestBooks.document(
                units.stream().filter(unit -> !unit.segments().isEmpty()).toArray(Unit[]::new));

        assertThat(terms(candidates(book, 1))).containsExactlyElementsOf(split(expected));
    }

    static Stream<Arguments> nonStoryMentions() {
        final List<String> eight = copies("Quillon", 8);
        final List<String> eightLines = copies("Quillon Brevik", 8);
        return Stream.of(
                Arguments.of("a body heading", unit("h", UnitRole.BODY, SegmentKind.HEADING, eight), "Quillon"),
                Arguments.of("a title", unit("h", UnitRole.BODY, SegmentKind.TITLE, eight), "Quillon"),
                Arguments.of("a contents entry", unit("h", UnitRole.BODY, SegmentKind.NAV_LABEL, eight), "Quillon"),
                Arguments.of(
                        "a paragraph of the front matter",
                        unit("f", UnitRole.FRONT_MATTER, SegmentKind.PARAGRAPH, eight),
                        "Quillon"),
                Arguments.of(
                        "a paragraph of the back matter",
                        unit("b", UnitRole.BACK_MATTER, SegmentKind.PARAGRAPH, eight),
                        "Quillon"),
                Arguments.of(
                        "a paragraph of the auxiliary unit",
                        unit(Unit.AUXILIARY_ID, UnitRole.BODY, SegmentKind.METADATA_TITLE, eight),
                        "Quillon"),
                Arguments.of(
                        "a two-word name in a heading",
                        unit("h", UnitRole.BODY, SegmentKind.HEADING, eightLines),
                        "Quillon Brevik"));
    }

    // IF the mentions outside the story are a heading, a title, a contents entry or a unit that is not the story, THEN
    // each counts against the name the same way; the story holds only two.
    @ParameterizedTest(name = "{0}")
    @MethodSource("nonStoryMentions")
    void candidates_mentionsOutsideTheStory_countAsHeadingMentions(
            final String description, final Unit outside, final String name) {
        final Document book =
                IndexTestBooks.document(outside, story("p", copies("Then we saw " + name + " at the gate.", 2)));

        assertThat(terms(candidates(book, 1))).isEmpty();
    }

    // IF a plain-text or FB2 book writes its headings as one-line paragraphs of capitalised words, THEN those lines are
    // heading mentions too, though their segments are paragraphs.
    @Test
    void candidates_titleLineParagraphs_countAsHeadingMentions() {
        final List<String> lines = new ArrayList<>(copies("Gain Stage", 3));
        lines.add("The manual names the Gain Stage there.");

        assertThat(terms(candidates(IndexTestBooks.oneUnit(lines.toArray(String[]::new)), 1)))
                .isEmpty();
    }

    // IF three mentions are in the story's text, THEN the title-line paragraphs do not drop the name.
    @Test
    void candidates_titleLineParagraphsAndThreeStoryMentions_keepsTheName() {
        final List<String> lines = new ArrayList<>(copies("Gain Stage", 3));
        lines.addAll(copies("The manual names the Gain Stage there.", 3));

        assertThat(terms(candidates(IndexTestBooks.oneUnit(lines.toArray(String[]::new)), 1)))
                .containsExactly("Gain Stage");
    }

    // IF the three story mentions are inside quotation marks, THEN they still count as story mentions: a name that is
    // spoken but never narrated is a name.
    @Test
    void candidates_storyMentionsOnlyInSpeech_keepsTheName() {
        final Document book = IndexTestBooks.document(
                unit("h", UnitRole.BODY, SegmentKind.HEADING, copies("Quillon", 8)),
                story("p", copies("Mara said, “We saw Quillon at the gate.”", 3)));

        assertThat(terms(candidates(book, 1))).containsExactly("Quillon");
    }

    // IF a name is written only outside the story (a converter's credit), THEN a scan over every segment drops it:
    // the triage measures the whole book, not the slice it was asked to scan.
    @Test
    void candidates_nameOnlyInNonStoryUnits_isDroppedEvenWhenTheSliceHoldsThem() {
        final Document book = IndexTestBooks.document(
                unit("f", UnitRole.FRONT_MATTER, SegmentKind.PARAGRAPH, copies("Converted by Jorun Falk.", 2)),
                unit("b", UnitRole.BACK_MATTER, SegmentKind.PARAGRAPH, copies("Edition prepared by Jorun Falk.", 2)),
                story("p", copies("Then we saw Quillon at the gate.", 3)));

        assertThat(terms(candidatesOfEverySegment(book, 1))).containsExactly("Quillon");
    }

    // IF a book writes a name only in a section of short credit lines (no paragraph of two full sentences), and another
    // section is prose, THEN the name is a converter's or publisher's credit and is dropped; one mention in the prose
    // keeps it, and a book with no prose paragraph anywhere drops nothing this way (15i.N5 junk).
    @ParameterizedTest(name = "story line \"{0}\", credit name in the story {1} -> [{2}]")
    @CsvSource({
        "We saw Ilsabet at dawn. She carried one letter., false, Ilsabet",
        "We saw Ilsabet at dawn. She carried one letter., true, Ilsabet|Jorvik Hale",
        "We saw Ilsabet at dawn., false, Ilsabet|Jorvik Hale"
    })
    void candidates_nameOnlyInACreditSection_isDroppedWhenTheBookHasProse(
            final String storyLine, final boolean inStory, final String expected) {
        final List<String> story = new ArrayList<>(copies(storyLine, 4));
        story.addAll(inStory ? List.of("Later Jorvik Hale wrote to her.") : List.of());
        final Document book = IndexTestBooks.document(
                story(
                        "notes",
                        List.of(
                                "Digital edition prepared by Jorvik Hale.",
                                "Thanks to Jorvik Hale for the scans.",
                                "Jorvik Hale wishes you a pleasant read.")),
                story("chapter", story));

        assertThat(terms(candidates(book, 1))).containsExactlyInAnyOrderElementsOf(split(expected));
    }

    // IF the name is mostly a heading across the whole book, THEN scanning only the chapter that mentions it
    // twice drops it all the same.
    @Test
    void candidates_sliceOfTheStory_isTriagedAgainstTheWholeBook() {
        final Document book = IndexTestBooks.document(
                unit("h", UnitRole.BODY, SegmentKind.HEADING, copies("Quillon", 8)), story("p", copies(NARRATION, 2)));
        final BookIndex index = BookIndex.of(book);

        final List<NameCandidate> found = FrequencyScan.candidates(index, index.story(), 1, "en");

        assertThat(found).isEmpty();
    }

    // ----- a name written as a pair -----

    // IF a pair is joined by "&" or "and" in 80 % or more of its uses, THEN it is one candidate and its halves are
    // not proposed alone; below that the halves stay separate candidates and the pair is not one.
    @ParameterizedTest(name = "{0} joined {1}, alone {2} each -> [{3}]")
    @CsvSource(
            delimiter = ';',
            value = {
                "&; 9; 1; Orrin & Vale",
                "&; 8; 2; Orrin & Vale",
                "&; 7; 3; Orrin|Vale",
                "and; 9; 1; Orrin and Vale",
                "and; 8; 2; Orrin and Vale",
                "and; 7; 3; Orrin|Vale"
            })
    void candidates_pairJoinedByConnector_isOneCandidateOnlyAtEightyPercent(
            final String connector, final int joined, final int alone, final String expected) {
        final List<String> lines =
                new ArrayList<>(copies("We stopped at Orrin " + connector + " Vale on the corner.", joined));
        lines.addAll(copies("We heard Orrin laugh.", alone));
        lines.addAll(copies("We heard Vale laugh.", alone));

        final List<String> found = terms(candidates(IndexTestBooks.oneUnit(lines.toArray(String[]::new)), 1));

        assertThat(found).containsExactlyInAnyOrderElementsOf(split(expected));
    }

    // IF a pair is one candidate, THEN its count is its joined uses and its first sentence is the first joined one.
    @Test
    void candidates_joinedPair_countsItsJoinedUsesAndShowsTheFirstJoinedSentence() {
        final List<String> lines = new ArrayList<>(copies("We heard Orrin laugh.", 1));
        lines.addAll(copies("We stopped at Orrin & Vale on the corner.", 9));
        lines.addAll(copies("We heard Vale laugh.", 1));

        final List<NameCandidate> found = candidates(IndexTestBooks.oneUnit(lines.toArray(String[]::new)), 1);

        assertThat(found)
                .extracting(NameCandidate::term, NameCandidate::count, NameCandidate::firstSentence)
                .containsExactly(tuple("Orrin & Vale", 9, "We stopped at Orrin & Vale on the corner."));
    }

    // IF one half is mostly written without the other (Vale: 9 joined of 20), THEN the pair is not one candidate: both
    // halves must be joined in 80 % of their uses.
    @Test
    void candidates_oneHalfOftenWrittenAlone_keepsBothHalvesSeparate() {
        final List<String> lines = new ArrayList<>(copies("We stopped at Orrin & Vale on the corner.", 9));
        lines.addAll(copies("We heard Orrin laugh.", 1));
        lines.addAll(copies("We heard Vale laugh.", 11));

        final List<String> found = terms(candidates(IndexTestBooks.oneUnit(lines.toArray(String[]::new)), 1));

        assertThat(found).containsExactlyInAnyOrder("Orrin", "Vale");
    }

    // IF the pair is joined often enough but a half is also part of a longer name, THEN the triage still leaves the
    // unrelated longer name alone.
    @Test
    void candidates_joinedPairBesideAnUnrelatedName_leavesTheOtherNameAlone() {
        final List<String> lines = new ArrayList<>(copies("We stopped at Orrin & Vale on the corner.", 9));
        lines.addAll(copies("We heard Orrin laugh.", 1));
        lines.addAll(copies("We heard Vale laugh.", 1));
        lines.addAll(copies("We saw Quillon at the gate.", 4));

        final List<String> found = terms(candidates(IndexTestBooks.oneUnit(lines.toArray(String[]::new)), 1));

        assertThat(found).containsExactlyInAnyOrder("Orrin & Vale", "Quillon");
    }

    // ----- spellings that differ only in letter case -----

    // IF two spellings differ only in letter case, THEN they are one candidate: the base is the form that is not
    // written in capitals, else the commoner one (the earlier on a tie), the other is its alias, and the counts add.
    @ParameterizedTest(name = "{0} x{1} with {2} x{3} -> {4}")
    @CsvSource({
        "KESSEL OPTIK, 5, Kessel Optik, 3, Kessel Optik",
        "Kessel Optik, 3, KESSEL OPTIK, 5, Kessel Optik",
        "HALE, 4, Hale, 2, Hale",
        "McKay, 4, Mckay, 2, McKay",
        "Mckay, 2, McKay, 4, McKay",
        "McKay, 3, Mckay, 3, McKay"
    })
    void candidates_caseVariants_foldIntoOneCandidateWithTheOtherAsAlias(
            final String first, final int firstCount, final String second, final int secondCount, final String base) {
        final List<String> lines = new ArrayList<>(copies("The label read " + first + " on the case.", firstCount));
        lines.addAll(copies("The label read " + second + " on the case.", secondCount));
        final String alias = base.equals(first) ? second : first;

        final List<NameCandidate> found = candidates(IndexTestBooks.oneUnit(lines.toArray(String[]::new)), 1);

        assertThat(found)
                .extracting(NameCandidate::term, NameCandidate::count, NameCandidate::aliases)
                .containsExactly(tuple(base, firstCount + secondCount, List.of(alias)));
    }

    // IF the glossary scan proposes a name that has a case variant, THEN the entry holds the variant as an alias, so
    // both spellings share one target.
    @Test
    void newTerms_caseVariant_isHeldByTheProposedEntryAsAnAlias() {
        final List<String> lines = new ArrayList<>(copies("The label read KESSEL OPTIK on the case.", 5));
        lines.addAll(copies("The label read Kessel Optik on the case.", 3));
        final GlossaryRepository glossary = Guice.createInjector(new DocumentModule(), new PersistenceModule())
                .getInstance(GlossaryRepository.class);
        final BookIndex index = BookIndex.of(IndexTestBooks.oneUnit(lines.toArray(String[]::new)));

        final List<GlossaryEntry> entries = FrequencyScan.newTerms("p1", index, index.story(), "en", glossary)
                .data();

        assertThat(entries)
                .extracting(GlossaryEntry::term, GlossaryEntry::aliases)
                .containsExactly(tuple("Kessel Optik", List.of("KESSEL OPTIK")));
    }
}
