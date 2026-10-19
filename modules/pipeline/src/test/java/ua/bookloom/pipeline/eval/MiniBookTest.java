package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.pipeline.eval.MiniBook.Block;
import ua.bookloom.pipeline.eval.MiniExpectations.Expectation;

/** The mini eval book and its expectation file hold together: ids line up, every defect is guarded, the EPUB is valid. */
class MiniBookTest {

    private static final MiniBook BOOK = MiniBook.load();
    private static final MiniExpectations EXPECTATIONS = MiniExpectations.load();

    @TempDir
    private Path dir;

    @Test
    void load_miniBook_holdsTwentyToThirtyConsecutivelyNumberedBlocks() {
        final List<String> ids = BOOK.blocks().stream().map(Block::id).toList();

        assertThat(ids).hasSizeBetween(20, 30);
        assertThat(ids)
                .isEqualTo(IntStream.rangeClosed(1, ids.size())
                        .mapToObj("m%02d"::formatted)
                        .toList());
    }

    @Test
    void load_miniBook_textsAreUniqueAndNeedNoXmlEscaping() {
        final List<String> texts = BOOK.blocks().stream().map(Block::text).toList();

        assertThat(texts).doesNotHaveDuplicates().noneMatch(text -> text.matches("(?s).*[&<>].*"));
    }

    @Test
    void load_expectations_idsAreUniqueAndNameBlocksOfTheBook() {
        final List<String> blockIds = BOOK.blocks().stream().map(Block::id).toList();
        final List<String> named = EXPECTATIONS.segments().stream()
                .flatMap(e -> Stream.concat(Optional.ofNullable(e.segment()).stream(), e.group().stream()))
                .toList();

        assertThat(EXPECTATIONS.ids()).doesNotHaveDuplicates();
        assertThat(blockIds).containsAll(named);
        assertThat(EXPECTATIONS.segments())
                .allSatisfy(e -> assertThat(e.segment() != null || !e.group().isEmpty())
                        .as(e.id())
                        .isTrue());
    }

    @Test
    void load_expectations_everyDefectOfTheRoundIsGuarded() {
        final Collection<String> guarded = Stream.of(
                        EXPECTATIONS.every().stream().map(Expectation::defect),
                        EXPECTATIONS.segments().stream().map(Expectation::defect),
                        EXPECTATIONS.glossary().stream().map(MiniExpectations.GlossaryExpectation::defect))
                .flatMap(s -> s)
                .collect(Collectors.toSet());

        assertThat(guarded)
                .contains(
                        "dialogue-punctuation",
                        "inflected-title",
                        "inflected-term",
                        "brand",
                        "material",
                        "drug",
                        "name-with-evidence",
                        "name-without-evidence",
                        "multi-word-place",
                        "transliteration",
                        "russian-letter",
                        "injection",
                        "long-paragraph",
                        "address-mix",
                        "first-person-narrator",
                        "footnote",
                        "latin-code");
    }

    @Test
    void load_expectations_brandsAreGuardedUnderEveryNamePolicy() {
        final var policies = EXPECTATIONS.segments().stream()
                .filter(e -> e.defect().equals("brand"))
                .flatMap(e -> e.policies().stream())
                .collect(Collectors.toSet());

        assertThat(policies).hasSize(3);
    }

    @Test
    void writeEpub_miniBook_putsMimetypeFirstAndStored() throws IOException {
        final Path file = BOOK.writeEpub(dir);

        try (ZipFile zip = new ZipFile(file.toFile())) {
            final ZipEntry first = zip.entries().nextElement();
            assertThat(first.getName()).isEqualTo("mimetype");
            assertThat(first.getMethod()).isEqualTo(ZipEntry.STORED);
            assertThat(zip.getEntry("OEBPS/content.opf")).isNotNull();
            assertThat(zip.getEntry("OEBPS/nav.xhtml")).isNotNull();
        }
    }
}
