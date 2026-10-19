package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.index.BookIndex;

/**
 * The name scan on the fixture book extended with the junk a published novel carried: a title page and a copyright
 * page naming the author and the publisher, a printing note and an "also by" list, and in the story an everyday
 * compound, a directory, a shouted place. The scan keeps the book's own names and terms and nothing else.
 */
class JunkFixtureScanTest {

    private static final Path EPUB = Path.of("../app/src/test/resources/fixtures/earth-gravity/earth-gravity.epub");
    private static final int PROPOSAL_COUNT = FrequencyScan.PROPOSAL_MIN_COUNT;
    private static final List<String> NAMES = List.of(
            "Eleanor Vance", "Vance", "Nell", "Tomas", "Reyes", "Harrow Vale", "Meridian Survey Institute", "Amulet");
    private static final List<String> OWN_TERMS =
            List.of("Earth", "Institute", "Moon", "NASA Solar System", "Exploration");
    private static final List<String> JUNK =
            List.of("T-shirt", "Yellow Pages", "BRANWICK", "Larkspur Books", "Hallam Crane", "Crane", "Hallam");

    @Test
    void candidates_fixtureWithJunkAdded_proposeOnlyTheNamesAndTheBooksOwnTerms() {
        final List<String> terms =
                namesOf(extended(), 1, "en").stream().map(NameCandidate::term).toList();

        assertThat(terms).doesNotContainAnyElementsOf(JUNK);
        assertThat(terms).containsExactlyInAnyOrderElementsOf(concat(NAMES, OWN_TERMS));
    }

    @Test
    void candidates_fixtureWithJunkAddedAtTheProposalCount_noJunkSurvives() {
        final List<String> terms = namesOf(extended(), PROPOSAL_COUNT, "en").stream()
                .map(NameCandidate::term)
                .toList();

        assertThat(terms).doesNotContainAnyElementsOf(JUNK);
    }

    @Test
    void story_fixtureEpub_leavesOutTitleCopyrightContentsAndNotes() {
        final Document book = open();

        assertThat(book.units())
                .filteredOn(unit -> !unit.isAuxiliary() && unit.role() != UnitRole.BODY)
                .extracting(Unit::id)
                .containsExactly(
                        "OEBPS/text/cover.xhtml",
                        "OEBPS/text/title.xhtml",
                        "OEBPS/text/copyright.xhtml",
                        "OEBPS/text/contents.xhtml",
                        "OEBPS/text/notes.xhtml");
        final BookIndex index = BookIndex.of(book);
        assertThat(index.story())
                .extracting(ordinal -> index.segment(ordinal).unitId())
                .doesNotContain("OEBPS/text/title.xhtml", "OEBPS/text/copyright.xhtml", "aux");
    }

    private static Document open() {
        return Objects.requireNonNull(
                Guice.createInjector(new DocumentModule())
                        .getInstance(DocumentPort.class)
                        .open(EPUB)
                        .data(),
                "fixture");
    }

    private static Document extended() {
        final Document book = open();
        final List<Unit> units = new ArrayList<>(book.units());
        units.addFirst(unit("junk-front", UnitRole.FRONT_MATTER, front()));
        units.add(unit("junk-body", UnitRole.BODY, story()));
        units.add(unit("junk-back", UnitRole.BACK_MATTER, back()));
        return book.withUnits(units);
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

    private static List<String> copies(final String line, final int count) {
        return Collections.nCopies(count, line);
    }

    private static List<String> concat(final List<String> first, final List<String> second) {
        final List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private static List<NameCandidate> namesOf(final Document book, final int minCount, final String language) {
        final BookIndex index = BookIndex.of(book);
        return FrequencyScan.candidates(index, index.story(), minCount, language);
    }
}
