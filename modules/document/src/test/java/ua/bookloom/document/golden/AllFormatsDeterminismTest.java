package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.DocumentServices;
import ua.bookloom.document.fixture.FixtureCatalog;

/**
 * Parse determinism across <strong>every</strong> catalogued fixture, not just an EPUB.
 *
 * <p>The requirement's own reason for existing is that the number of parsers this must hold for goes from one to
 * four: anchors are never persisted, so on resume they are recomputed by parsing the file again, and a parser that
 * numbered its units or blocks differently the second time would write every translation into the wrong place
 * after a restart — silently. Proving it for one format would leave three unproven for exactly the reason the
 * requirement was written.
 */
class AllFormatsDeterminismTest {

    @TempDir
    private Path tempDir;

    private static List<FixtureCatalog.Case> catalogue() {
        return FixtureCatalog.all();
    }

    // Covers: FR-IMPORT-08 — WHEN the same source bytes are parsed twice, THEN both parses produce the same unit
    // ids in the same order and every segment's id, order and anchor is identical between them.
    @ParameterizedTest(name = "{0}")
    @MethodSource("catalogue")
    void read_everyFormatParsedTwice_yieldsIdenticalIdsOrderAndAnchors(FixtureCatalog.Case fixture) {
        final Path source = fixture.builder().apply(tempDir.resolve(fixture.fileName()));

        final Document first = open(source);
        final Document second = open(source);

        assertThat(unitIdsOf(second)).as("unit ids").isEqualTo(unitIdsOf(first));
        assertThat(identitiesOf(second)).as("segment ids, order and anchors").isEqualTo(identitiesOf(first));
    }

    private static Document open(Path source) {
        return Objects.requireNonNull(DocumentServices.newService().open(source).data(), "fixture did not open");
    }

    private static List<String> unitIdsOf(Document document) {
        final List<String> ids = new ArrayList<>();
        for (final Unit unit : document.units()) {
            ids.add(unit.id());
        }
        return ids;
    }

    /** Each segment reduced to exactly what resume depends on: its id, its order and its anchor. */
    private static List<String> identitiesOf(Document document) {
        final List<String> identities = new ArrayList<>();
        for (final Unit unit : document.units()) {
            appendIdentities(unit, identities);
        }
        return identities;
    }

    private static void appendIdentities(Unit unit, List<String> identities) {
        for (final Segment segment : unit.segments()) {
            identities.add(segment.id() + "|" + segment.order() + "|" + segment.anchor());
        }
    }
}
