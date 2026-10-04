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
            List.of("T-shirt", "Yellow Pages", "CROYDON", "Hyperion Books", "Jonathan Stroud", "Stroud", "Jonathan");

    @Test
    void candidates_fixtureWithJunkAdded_proposeOnlyTheNamesAndTheBooksOwnTerms() {
        final List<String> terms = FrequencyScan.candidates(FrequencyScan.storyText(extended()), 1, "en").stream()
                .map(NameCandidate::term)
                .toList();

        assertThat(terms).doesNotContainAnyElementsOf(JUNK);
        assertThat(terms).containsExactlyInAnyOrderElementsOf(concat(NAMES, OWN_TERMS));
    }

    @Test
    void candidates_fixtureWithJunkAddedAtTheProposalCount_noJunkSurvives() {
        final List<String> terms =
                FrequencyScan.candidates(FrequencyScan.storyText(extended()), PROPOSAL_COUNT, "en").stream()
                        .map(NameCandidate::term)
                        .toList();

        assertThat(terms).doesNotContainAnyElementsOf(JUNK);
    }

    @Test
    void storyText_fixtureEpub_leavesOutTitleCopyrightContentsAndNotes() {
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
        assertThat(FrequencyScan.storyText(book))
                .extracting(segment -> segment.unit())
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
        lines.addAll(copies("Jonathan Stroud", 4));
        lines.addAll(copies("Published by Hyperion Books in New York", 4));
        lines.addAll(copies("Hyperion Books and Jonathan Stroud, Printed in CROYDON", 4));
        return lines;
    }

    private static List<String> story() {
        final List<String> lines = new ArrayList<>();
        lines.addAll(copies("The boy wore a stained T-shirt that day.", 4));
        lines.addAll(copies("They looked it up in the Yellow Pages again.", 4));
        lines.addAll(copies("The sign over the road said CROYDON in red.", 4));
        lines.addAll(copies("Printed in CROYDON by CPI Books and sold by Hyperion Books", 4));
        lines.addAll(copies("Text copyright © 2003 by Jonathan Stroud", 4));
        return lines;
    }

    private static List<String> back() {
        return copies("Also by Jonathan Stroud: Hyperion Books titles", 4);
    }

    private static Unit unit(final String id, final UnitRole role, final List<String> paragraphs) {
        return new Unit(
                id,
                0,
                id + ".xhtml",
                "application/xhtml+xml",
                new SkeletonHandle(id),
                GlossaryTestSegments.of(paragraphs),
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
}
