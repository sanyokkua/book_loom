package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.DocumentService;
import ua.bookloom.document.DocumentServices;
import ua.bookloom.document.golden.XmlLeafDiff.Change;

/**
 * The auxiliary-text obligations of the EPUB golden gate (task 6.6): reading more slots must not make a zero-edit
 * write less faithful, an edit to auxiliary slots changes only their values and the language metadata, and a new
 * table-of-contents entry must not move the title or the authors.
 */
class EpubAuxiliaryGoldenTest {

    private static final List<String> COMPARED_ENTRIES =
            List.of("OEBPS/content.opf", "OEBPS/c01.xhtml", "OEBPS/toc01.html", "OEBPS/toc.ncx");

    @TempDir
    private Path tempDir;

    // Auxiliary text is read and written back: with no target anywhere, entry by entry the output is the source's
    // canonical form, the navigation document and the NCX included.
    @Test
    void write_auxiliaryFixtureWithNoTargets_isCanonicalEqualToSource() {
        final Path source = AuxiliaryFixtureEpub.build(tempDir.resolve("aux.epub"), AuxiliaryFixtureEpub.LABELS);
        final DocumentService service = DocumentServices.newService();
        final Document document = open(service, source);

        final Path output = write(service, document, "en");

        EpubCanonicalAssert.assertCanonicalEqual(source, output);
        assertThat(auxiliaryOf(document).segments()).extracting(Segment::id).contains("aux:title", "aux:nav:2");
    }

    // Editing the title, one navigation label (whose NCX label reads the same) and one image's alt, and no body
    // segment, changes exactly four values: the title, the navigation label, the NCX label written from it and the
    // alt — plus the language metadata.
    @Test
    void write_onlyAuxiliarySlotsEdited_changesExactlyThoseValuesAndTheLanguageMetadata() {
        final Path source = AuxiliaryFixtureEpub.build(tempDir.resolve("aux.epub"), AuxiliaryFixtureEpub.LABELS);
        final DocumentService service = DocumentServices.newService();
        final Document document = open(service, source);
        final String altId = auxiliaryOf(document).segments().stream()
                .map(Segment::id)
                .filter(id -> id.startsWith("aux:alt:"))
                .findFirst()
                .orElseThrow();
        final Document edited = AuxiliaryTargets.with(
                document, Map.of("aux:title", "Франкенштейн", "aux:nav:2", "Буря", altId, "Рисунок 1"));

        final Path output = write(service, edited, "uk");

        final List<Change> changes = changesBetween(source, output);
        assertThat(changes)
                .filteredOn(change -> !change.isLanguage())
                .extracting(Change::oldValue, Change::newValue)
                .containsExactlyInAnyOrder(
                        tuple("Frankenstein", "Франкенштейн"),
                        tuple("The Storm", "Буря"),
                        tuple("The Storm", "Буря"),
                        tuple("Figure 1", "Рисунок 1"));
        assertThat(changes)
                .filteredOn(Change::isLanguage)
                .isNotEmpty()
                .extracting(Change::newValue)
                .containsOnly("uk");
    }

    // A navigation entry added at the start renumbers the navigation labels but neither the title, the first
    // author nor any body segment.
    @Test
    void read_navigationEntryAddedAtTheStart_keepsTitleFirstAuthorAndBodyIdsStable() {
        final List<String> longer = new ArrayList<>(AuxiliaryFixtureEpub.LABELS);
        longer.addFirst("Preface");
        final DocumentService service = DocumentServices.newService();

        final Document before =
                open(service, AuxiliaryFixtureEpub.build(tempDir.resolve("three.epub"), AuxiliaryFixtureEpub.LABELS));
        final Document after = open(service, AuxiliaryFixtureEpub.build(tempDir.resolve("four.epub"), longer));

        assertThat(auxiliaryOf(before).segments()).extracting(Segment::id).contains("aux:nav:3");
        assertThat(auxiliaryOf(after).segments()).extracting(Segment::id).contains("aux:nav:4");
        assertThat(auxiliaryOf(after).segments())
                .filteredOn(segment ->
                        segment.id().equals("aux:title") || segment.id().equals("aux:creator:0"))
                .extracting(Segment::id, Segment::sourceInner)
                .containsExactly(tuple("aux:title", "Frankenstein"), tuple("aux:creator:0", "Mary Shelley"));
        assertThat(bodySegmentIds(after)).isNotEmpty().isEqualTo(bodySegmentIds(before));
    }

    private static List<Change> changesBetween(Path source, Path output) {
        final List<Change> changes = new ArrayList<>();
        for (final String entry : COMPARED_ENTRIES) {
            for (final Change change : XmlLeafDiff.between(bytesOf(source, entry), bytesOf(output, entry))) {
                changes.add(new Change(entry + ":" + change.path(), change.oldValue(), change.newValue()));
            }
        }
        return changes;
    }

    private static List<String> bodySegmentIds(Document document) {
        return document.units().stream()
                .filter(unit -> !unit.isAuxiliary())
                .flatMap(unit -> unit.segments().stream())
                .map(Segment::id)
                .toList();
    }

    private static Unit auxiliaryOf(Document document) {
        return document.units().stream().filter(Unit::isAuxiliary).findFirst().orElseThrow();
    }

    private static Document open(DocumentService service, Path source) {
        return Objects.requireNonNull(service.open(source).data(), "data");
    }

    private Path write(DocumentService service, Document document, String targetLanguage) {
        return Objects.requireNonNull(
                service.write(document, tempDir.resolve("out.epub"), targetLanguage)
                        .data(),
                "data");
    }

    private static byte[] bytesOf(Path zip, String entryName) {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            final ZipEntry entry = Objects.requireNonNull(file.getEntry(entryName), entryName);
            return file.getInputStream(entry).readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
