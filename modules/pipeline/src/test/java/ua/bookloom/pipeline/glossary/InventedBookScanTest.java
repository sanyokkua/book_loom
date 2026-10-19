package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.pipeline.index.BookIndex;

/**
 * The name scan on a small invented book that reproduces what the scan got wrong on a real novel: interjections and
 * contractions after a dash or a quote, common nouns written with a capital, a hyphenated place cut in two, the
 * forename or surname of a longer name proposed on its own, and the junk of a published book (a title page naming the
 * author and the publisher, a printing note, an "also by" list, an everyday compound, a directory, a shouted place).
 */
class InventedBookScanTest {

    private static final int THRESHOLD = 3;
    private static final int EVERY_MENTION = 1;
    private static final List<String> JUNK =
            List.of("T-shirt", "Yellow Pages", "BRANWICK", "Larkspur Books", "Hallam Crane", "Crane", "Hallam");

    @Test
    void candidates_book_proposesTheNames() {
        assertThat(terms(THRESHOLD))
                .contains("Edwin", "Zerubin", "Pellbury", "Garrick Ashdown", "Ashdown", "Ansel Brack", "El-Barun");
    }

    @Test
    void candidates_book_leavesOutCommonWordsAndPartsOfLongerNames() {
        assertThat(terms(THRESHOLD))
                .doesNotContain(
                        "Well", "Don", "Don’t", "I’m", "Words", "Seal", "Shield", "Shields", "Eastern", "El", "Barun",
                        "Garrick", "Ansel", "Brack");
    }

    @Test
    void candidates_sentenceInitialRunWhoseHeadIsSeenMidSentence_countsTheWholeRun() {
        final List<NameCandidate> found = namesOf(book(), THRESHOLD, "en");

        assertThat(found)
                .filteredOn(candidate -> candidate.term().equals("Ansel Brack"))
                .singleElement()
                .extracting(NameCandidate::count)
                .isEqualTo(7);
    }

    @Test
    void candidates_bookAtTheProposalCount_noFrontMatterOrPrintingNoteSurvives() {
        assertThat(terms(FrequencyScan.PROPOSAL_MIN_COUNT)).doesNotContainAnyElementsOf(JUNK);
    }

    // The proposal threshold hides single mentions; at one, front and back matter and printing notes must still not
    // show.
    @Test
    void candidates_bookAtEveryMention_hasNoPrintingNoteOrAuthorLine() {
        assertThat(terms(EVERY_MENTION)).doesNotContainAnyElementsOf(JUNK);
    }

    private static List<String> terms(final int minCount) {
        return namesOf(book(), minCount, "en").stream().map(NameCandidate::term).toList();
    }

    private static Document book() {
        return new Document(
                "invented-book",
                BookFormat.EPUB,
                "en",
                null,
                null,
                null,
                "hash",
                Map.of(),
                List.of(
                        unit("front", UnitRole.FRONT_MATTER, front()),
                        unit("story", UnitRole.BODY, story()),
                        unit("back", UnitRole.BACK_MATTER, back())));
    }

    private static List<String> front() {
        final List<String> lines = new ArrayList<>();
        lines.addAll(copies("Hallam Crane", 4));
        lines.addAll(copies("Published by Larkspur Books in New York", 4));
        lines.addAll(copies("Larkspur Books and Hallam Crane, Printed in BRANWICK", 4));
        return lines;
    }

    private static List<String> story() {
        final List<String> lines = new ArrayList<>();
        lines.addAll(copies("The boy Edwin ran up the stairs.", 5));
        lines.addAll(copies("The djinni Zerubin grinned.", 5));
        lines.addAll(copies("His master, Pellbury, was waiting.", 4));
        lines.add("They called on Arthur Pellbury at noon.");
        lines.addAll(copies("The magician Garrick Ashdown smiled.", 6));
        lines.addAll(copies("He looked at Ashdown again.", 6));
        lines.addAll(copies("Even Garrick was afraid.", 3));
        lines.addAll(copies("They visited Ansel Brack at his shop.", 4));
        lines.addAll(copies("Ansel Brack bowed low.", 3));
        lines.addAll(copies("He paused—Well, perhaps not.", 3));
        lines.addAll(copies("He knew the house well.", 3));
        lines.addAll(copies("She said, \"Don’t go.\"", 3));
        lines.addAll(copies("You don’t understand.", 4));
        lines.addAll(copies("I’m late, she said.", 3));
        lines.addAll(copies("He spoke the Words aloud.", 3));
        lines.addAll(copies("Those words meant nothing.", 2));
        lines.addAll(copies("He broke the Seal at once.", 3));
        lines.addAll(copies("The seal cracked.", 2));
        lines.addAll(copies("He raised a Shield around him.", 3));
        lines.add("His shield held.");
        lines.addAll(copies("He rode toward the Eastern gate.", 3));
        lines.addAll(copies("The eastern road was long.", 2));
        lines.addAll(copies("They took the road to El-Barun that day.", 3));
        lines.addAll(junk());
        return lines;
    }

    private static List<String> junk() {
        final List<String> lines = new ArrayList<>();
        lines.addAll(copies("The boy wore a stained T-shirt that day.", 4));
        lines.addAll(copies("They looked it up in the Yellow Pages again.", 4));
        lines.addAll(copies("The sign over the road said BRANWICK in red.", 4));
        lines.addAll(copies("Printed in BRANWICK by CPI Books and sold by Larkspur Books", 4));
        lines.addAll(copies("Text copyright © 2003 by Hallam Crane", 4));
        return lines;
    }

    private static List<String> back() {
        return copies("Also by Hallam Crane: Larkspur Books titles", 4);
    }

    private static Unit unit(final String id, final UnitRole role, final List<String> paragraphs) {
        return new Unit(
                id,
                0,
                id + ".xhtml",
                "application/xhtml+xml",
                new SkeletonHandle(id),
                GlossaryTestSegments.segments(paragraphs),
                role);
    }

    private static List<String> copies(final String line, final int times) {
        return Collections.nCopies(times, line);
    }

    private static List<NameCandidate> namesOf(final Document book, final int minCount, final String language) {
        final BookIndex index = BookIndex.of(book);
        return FrequencyScan.candidates(index, index.story(), minCount, language);
    }
}
