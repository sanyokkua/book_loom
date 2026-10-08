package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.SegmentPreview;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.TestBooks;

/** The Structure screen's segment listing over the real document module, checked against the plan it must agree with. */
class ProjectServiceSegmentsTest {

    private static final String SHORT = "A short paragraph.";

    @TempDir
    private Path tempDir;

    private ProjectService service;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(new DocumentModule(), new PersistenceModule());
        service = injector.getInstance(ProjectServiceImpl.class);
    }

    @Test
    void segments_tenShortParagraphs_putsEightInTheFirstChunkAndTwoInTheSecond() {
        final String id = importId(markdownOf(SHORT, SHORT, SHORT, SHORT, SHORT, SHORT, SHORT, SHORT, SHORT, SHORT));

        final List<SegmentPreview> listed = segmentsOk(id, "Book.md");

        assertThat(listed).hasSize(10);
        assertThat(listed.stream().map(SegmentPreview::chunkIndex).toList())
                .containsExactly(1, 1, 1, 1, 1, 1, 1, 1, 2, 2);
        assertThat(listed).allSatisfy(one -> {
            assertThat(one.chunkCount()).isEqualTo(2);
            assertThat(one.displaySource()).isEqualTo(SHORT);
            assertThat(one.keptVerbatim()).isFalse();
            assertThat(one.keptAsSource()).isFalse();
            assertThat(one.tokenEstimate()).isPositive();
        });
        assertThat(listed.get(0).segmentId()).isEqualTo("Book.md:0");
        assertThat(listed.get(0).locator()).isEqualTo("ch1 · p01");
        assertThat(listed.get(9).locator()).isEqualTo("ch1 · p10");
    }

    @ParameterizedTest
    @ValueSource(strings = {"epub", "fb2", "md", "txt"})
    void segments_everyFormat_chunkCountsMatchThePlan(final String format) {
        final String id = importId(multiChapterBook(format));
        final BookPlan plan = planOk(id);

        plan.chunksPerUnit().forEach((unitId, chunks) -> {
            final List<SegmentPreview> listed = segmentsOk(id, unitId);
            final long distinct = listed.stream()
                    .map(SegmentPreview::chunkIndex)
                    .filter(i -> i > 0)
                    .distinct()
                    .count();
            assertThat((int) distinct).as("chunks of %s", unitId).isEqualTo(chunks);
        });
        assertThat(plan.chunksPerUnit().values().stream()
                        .mapToInt(Integer::intValue)
                        .sum())
                .isPositive();
    }

    @Test
    void segments_numeralOnlyParagraph_isKeptVerbatim() {
        final String id = importId(markdownOf("It was a dark night.", "2", "The rain kept falling."));

        final List<SegmentPreview> listed = segmentsOk(id, "Book.md");

        assertThat(listed.stream().map(SegmentPreview::keptVerbatim).toList()).containsExactly(false, true, false);
        assertThat(listed.get(1).displaySource()).isEqualTo("2");
    }

    @Test
    void segments_auxiliaryKindTheBriefLeavesUntranslated_isKeptAsSourceAndNotChunked() {
        final ImportedBook imported = importOk(TestBooks.epub(tempDir.resolve("b.epub"), twoChapters(), "en"));
        final String id = Objects.requireNonNull(imported.projectId());
        final BookBrief brief = Objects.requireNonNull(imported.brief());
        assertThat(segmentsOk(id, Unit.AUXILIARY_ID)).noneMatch(SegmentPreview::keptAsSource);

        service.updateBrief(id, withSwitches(brief, new AlsoTranslate(false, true, false, false)));
        final List<SegmentPreview> listed = segmentsOk(id, Unit.AUXILIARY_ID);

        assertThat(listed).isNotEmpty();
        assertThat(listed)
                .filteredOn(one -> one.kind() == SegmentKind.METADATA_TITLE || one.kind() == SegmentKind.NAV_LABEL)
                .isNotEmpty()
                .allSatisfy(one -> {
                    assertThat(one.keptAsSource()).isTrue();
                    assertThat(one.chunkIndex()).isZero();
                    assertThat(one.chunkCount()).isZero();
                });
        assertThat(planOk(id).chunksPerUnit().getOrDefault(Unit.AUXILIARY_ID, 0))
                .isEqualTo((int) listed.stream()
                        .map(SegmentPreview::chunkIndex)
                        .filter(i -> i > 0)
                        .distinct()
                        .count());
    }

    @Test
    void segments_afterTheBriefChangesTheDial_maxHoldsTwoSegmentsPerChunk() {
        final ImportedBook imported = importOk(markdownOf(SHORT, SHORT, SHORT, SHORT));
        final String id = Objects.requireNonNull(imported.projectId());
        assertThat(segmentsOk(id, "Book.md"))
                .allSatisfy(one -> assertThat(one.chunkCount()).isEqualTo(1));

        service.updateBrief(id, Objects.requireNonNull(imported.brief()).withDial(QualityDial.MAX));

        assertThat(segmentsOk(id, "Book.md").stream()
                        .map(SegmentPreview::chunkIndex)
                        .toList())
                .containsExactly(1, 1, 2, 2);
    }

    @Test
    void segments_unknownProjectOrUnit_answerValidation() {
        final String id = importId(markdownOf(SHORT));

        assertThat(codeOf(service.segments("missing", "Book.md"))).isEqualTo(ErrorCode.validation);
        assertThat(codeOf(service.segments(id, "nope"))).isEqualTo(ErrorCode.validation);
        assertThat(codeOf(service.segments(id, " "))).isEqualTo(ErrorCode.validation);
    }

    private ImportedBook importOk(final Path book) {
        final Result<ImportedBook> result = service.importBook(book);
        assertThat(result.isOk()).as("import %s", result.error()).isTrue();
        return Objects.requireNonNull(result.data());
    }

    private String importId(final Path book) {
        return Objects.requireNonNull(importOk(book).projectId());
    }

    private Path markdownOf(final String... paragraphs) {
        return TestBooks.markdown(tempDir.resolve("Book.md"), String.join("\n\n", paragraphs) + "\n");
    }

    private static List<List<String>> twoChapters() {
        return List.of(
                Collections.nCopies(10, SHORT),
                List.of("The rain kept falling.", "A second paragraph of chapter two."));
    }

    private Path multiChapterBook(final String format) {
        final List<String> flat = twoChapters().stream().flatMap(List::stream).toList();
        final Path target = tempDir.resolve("multi." + format);
        return switch (format) {
            case "epub" -> TestBooks.epub(target, twoChapters(), "en");
            case "fb2" -> TestBooks.fb2(target, flat, "en");
            case "md" -> TestBooks.markdown(target, String.join("\n\n", flat) + "\n");
            default -> TestBooks.txt(target, String.join("\n\n", flat) + "\n");
        };
    }

    private List<SegmentPreview> segmentsOk(final String projectId, final String unitId) {
        final Result<List<SegmentPreview>> result = service.segments(projectId, unitId);
        assertThat(result.isOk()).as("segments %s", result.error()).isTrue();
        return Objects.requireNonNull(result.data());
    }

    private BookPlan planOk(final String projectId) {
        return Objects.requireNonNull(service.plan(projectId).data());
    }

    private static ErrorCode codeOf(final Result<?> result) {
        return Objects.requireNonNull(result.error()).code();
    }

    private static BookBrief withSwitches(final BookBrief brief, final AlsoTranslate switches) {
        return new BookBrief(
                brief.sourceLanguage(),
                brief.targetLanguage(),
                brief.genre(),
                brief.register(),
                brief.voiceEra(),
                brief.audience(),
                brief.names(),
                brief.foreignPassages(),
                brief.footnotes(),
                brief.units(),
                brief.balance(),
                switches,
                brief.dial());
    }
}
