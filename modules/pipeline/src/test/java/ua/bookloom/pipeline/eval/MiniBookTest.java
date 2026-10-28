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
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.pipeline.eval.MiniBook.Block;
import ua.bookloom.pipeline.eval.MiniExpectations.Expectation;

/** The mini eval book and its expectation file hold together: ids line up, every defect is guarded, the EPUB is valid. */
class MiniBookTest {

    private static final MiniBook BOOK = MiniBook.load(MiniBook.MINI_EN);
    private static final MiniExpectations EXPECTATIONS = MiniExpectations.load(MiniBook.MINI_EN);

    @TempDir
    private Path dir;

    @Test
    void load_miniBook_holdsTwentyToFortyConsecutivelyNumberedBlocks() {
        final List<String> ids = BOOK.blocks().stream().map(Block::id).toList();

        assertThat(ids).hasSizeBetween(20, 40);
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
    void load_miniEn_hasTwoChaptersAndTheNumberedHeading() {
        final List<String> headings = BOOK.blocks().stream()
                .filter(b -> b.kind() == MiniBook.Kind.H)
                .map(Block::text)
                .toList();

        assertThat(headings).hasSize(2).contains("3. The Lamp Room");
        assertThat(BOOK.pair()).isEqualTo("en-uk");
        assertThat(BOOK.narrator().person()).isEqualTo(NarratorPerson.FIRST);
        assertThat(BOOK.narrator().gender()).isEqualTo(Gender.MALE);
    }

    @Test
    void load_miniRu_hasSeventeenBlocksInTwoChapters() {
        final MiniBook ru = MiniBook.load(MiniBook.MINI_RU);

        assertThat(ru.blocks()).hasSize(17);
        assertThat(ru.blocks().stream().filter(b -> b.kind() == MiniBook.Kind.H).map(Block::text))
                .containsExactly("4. Ночной перевал", "5. Утренний обход");
        assertThat(ru.blocks()).extracting(Block::id).allMatch(id -> id.matches("ch0[12]\\.md:\\d+"));
        assertThat(ru.blocks()).extracting(Block::id).contains("ch02.md:14", "ch02.md:16");
        assertThat(ru.pair()).isEqualTo("ru-uk");
        assertThat(ru.narrator().person()).isEqualTo(NarratorPerson.FIRST);
        assertThat(ru.narrator().gender()).isEqualTo(Gender.UNKNOWN);
    }

    @Test
    void load_miniRu_labelsExpectationsAndManifestLineUpWithTheBook() {
        final MiniBook ru = MiniBook.load(MiniBook.MINI_RU);
        final MiniExpectations expectations = MiniExpectations.load(MiniBook.MINI_RU);
        final List<String> blockIds = ru.blocks().stream().map(Block::id).toList();
        final List<String> named = expectations.segments().stream()
                .flatMap(e -> Stream.concat(Optional.ofNullable(e.segment()).stream(), e.group().stream()))
                .toList();

        assertThat(MiniLabels.load(MiniBook.MINI_RU).byBlock().keySet()).containsExactlyElementsOf(blockIds);
        assertThat(blockIds).containsAll(named);
        assertThat(expectations.ids()).doesNotHaveDuplicates();
        assertThat(DatasetManifest.load(datasetFolder(MiniBook.MINI_RU)).pair()).isEqualTo("ru-uk");
        assertThat(MiniGold.load(MiniBook.MINI_RU))
                .extracting(MiniGold.Entry::lemma)
                .contains("Велемир");
    }

    @Test
    void load_miniRu_theProofreadAndGenderCasesCarryTheirReferenceTexts() {
        final MiniBook ru = MiniBook.load(MiniBook.MINI_RU);

        assertThat(ru.blocks())
                .filteredOn(b -> b.id().equals("ch02.md:14"))
                .extracting(Block::text, Block::reference)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "Мирена кивнула и сказала, что ключ у Тарна.", "Мирена кивнула й сказала, що ключ у Тарна."));
        assertThat(ru.blocks())
                .filteredOn(b -> b.id().equals("ch02.md:16"))
                .extracting(Block::text, Block::reference)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "Ивар сказал, что вернётся.", "Івар сказав, що повернеться."));
    }

    @Test
    void load_miniEn_pairMatchesTheManifestAndTheLabelsCoverEveryBlock() {
        final Path folder = datasetFolder();

        assertThat(DatasetManifest.load(folder).pair()).isEqualTo(BOOK.pair());
        assertThat(MiniLabels.load(MiniBook.MINI_EN).byBlock().keySet())
                .containsExactlyElementsOf(BOOK.blocks().stream().map(Block::id).toList());
    }

    @Test
    void load_miniEn_tobinReedIsNamedOnlyInTheSecondChapter() {
        final int secondChapter = BOOK.blocks().stream()
                .filter(b -> b.text().startsWith("3."))
                .map(b -> BOOK.blocks().indexOf(b))
                .findFirst()
                .orElseThrow();

        assertThat(BOOK.blocks().subList(0, secondChapter))
                .noneMatch(b -> b.text().contains("Tobin"));
        assertThat(BOOK.blocks().subList(secondChapter, BOOK.blocks().size()))
                .anyMatch(b -> b.text().contains("Tobin Reed"));
    }

    @Test
    void load_goldMap_everyFormOccursInTheBookAndPellsTargetCarriesAStressMark() {
        final var gold = MiniGold.load(MiniBook.MINI_EN);
        final String text = BOOK.blocks().stream().map(Block::text).collect(Collectors.joining("\n"));

        assertThat(gold).extracting(MiniGold.Entry::lemma).contains("Pell", "Tobin Reed", "Ilsabet Corwen");
        assertThat(gold).allSatisfy(e -> assertThat(e.forms()).allMatch(text::contains));
        assertThat(gold)
                .filteredOn(e -> e.lemma().equals("Pell"))
                .allMatch(e -> e.target().contains("\u0301"));
    }

    private static Path datasetFolder() {
        return datasetFolder(MiniBook.MINI_EN);
    }

    private static Path datasetFolder(final String dataset) {
        try {
            return Path.of(MiniBookTest.class
                            .getResource("/eval/datasets/" + dataset + "/manifest.json")
                            .toURI())
                    .getParent();
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
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
