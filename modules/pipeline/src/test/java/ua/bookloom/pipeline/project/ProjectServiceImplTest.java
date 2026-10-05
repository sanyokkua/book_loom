package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.TestBooks;

/** Import, brief update, plan and close over the real document module and the in-memory repositories. */
class ProjectServiceImplTest {

    private static final List<List<String>> TWO_CHAPTERS =
            List.of(List.of("It was a dark night."), List.of("The rain kept falling."));

    @TempDir
    private Path tempDir;

    private ProjectService service;
    private DocumentPort documents;
    private ProjectRepository projects;
    private SegmentRepository segments;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(new DocumentModule(), new PersistenceModule());
        service = injector.getInstance(ProjectServiceImpl.class);
        documents = injector.getInstance(DocumentPort.class);
        projects = injector.getInstance(ProjectRepository.class);
        segments = injector.getInstance(SegmentRepository.class);
    }

    @Test
    void importBook_epubDeclaringEnUs_storesProjectWithSourceEnAndOnePendingRecordPerSegment() {
        final Path book = TestBooks.epub(tempDir.resolve("book.epub"), TWO_CHAPTERS, "en-US");

        final ImportedBook imported = importOk(book);

        assertThat(idOf(imported)).hasSize(12).matches("[0-9a-f]{12}");
        assertThat(imported.inspection().verdict()).isEqualTo(InspectionVerdict.READABLE);
        assertThat(imported.profile()).isNotNull();
        assertThat(briefOf(imported)).isNotNull();
        assertThat(briefOf(imported).sourceLanguage()).isEqualTo("en");
        assertThat(storedProject(imported).brief()).isEqualTo(briefOf(imported));
        final List<SegmentRecord> records = records(imported);
        assertThat(records).hasSize(segmentCount(book)).isNotEmpty();
        assertThat(records).allSatisfy(record -> assertThat(record.status()).isEqualTo(SegmentStatus.PENDING));
        assertThat(records).anySatisfy(record -> assertThat(record.unitId()).isEqualTo(Unit.AUXILIARY_ID));
    }

    @Test
    void importBook_packageEnButContentUk_preselectsContentMajority() {
        final Path book = TestBooks.epub(tempDir.resolve("book.epub"), TWO_CHAPTERS, "en", "uk");

        assertThat(briefOf(importOk(book)).sourceLanguage()).isEqualTo("uk");
    }

    @Test
    void importBook_noPackageLanguageContentDe_preselectsContentMajority() {
        final Path book = TestBooks.epub(tempDir.resolve("book.epub"), TWO_CHAPTERS, null, "de");

        assertThat(briefOf(importOk(book)).sourceLanguage()).isEqualTo("de");
    }

    @Test
    void importBook_epubDeclaringLatin_preselectsLatin() {
        final Path book = TestBooks.epub(tempDir.resolve("book.epub"), TWO_CHAPTERS, "la");

        assertThat(briefOf(importOk(book)).sourceLanguage()).isEqualTo("la");
    }

    @Test
    void importBook_unrecognizedDeclaration_preselectsNothing() {
        final Path book = TestBooks.epub(tempDir.resolve("book.epub"), TWO_CHAPTERS, "xx-yy");

        assertThat(briefOf(importOk(book)).sourceLanguage()).isNull();
    }

    @Test
    void importBook_plainText_preselectsNothing() {
        final Path book = TestBooks.txt(tempDir.resolve("book.txt"), "It was a dark night.\n\nThe rain kept falling.");

        assertThat(briefOf(importOk(book)).sourceLanguage()).isNull();
    }

    @Test
    void importBook_drmProtectedEpub_returnsVerdictWithNoProject() {
        final Path book = TestBooks.encryptedEpub(tempDir.resolve("locked.epub"), TWO_CHAPTERS);

        final ImportedBook imported = importOk(book);

        assertThat(imported.inspection().verdict()).isEqualTo(InspectionVerdict.DRM_PROTECTED);
        assertThat(imported.projectId()).isNull();
        assertThat(imported.profile()).isNull();
        assertThat(imported.brief()).isNull();
    }

    @Test
    void importBook_pdfFile_returnsUnsupportedVerdict() throws IOException {
        final Path book =
                Files.writeString(tempDir.resolve("notes.pdf"), "%PDF-1.7\n1 0 obj\n", StandardCharsets.UTF_8);

        final ImportedBook imported = importOk(book);

        assertThat(imported.inspection().verdict()).isEqualTo(InspectionVerdict.UNSUPPORTED);
        assertThat(imported.projectId()).isNull();
    }

    @Test
    void importBook_samePathTwice_yieldsSameIdAndResetsRecords() {
        final Path book = TestBooks.epub(tempDir.resolve("book.epub"), TWO_CHAPTERS, "en");
        final ImportedBook first = importOk(book);
        final String id = idOf(first);
        final SegmentRecord firstRecord = records(first).get(0);
        segments.update(id, firstRecord.segmentId(), record -> record.withStatus(SegmentStatus.ACCEPTED));
        assertThat(records(first).get(0).status()).isEqualTo(SegmentStatus.ACCEPTED);

        final ImportedBook second = importOk(book);

        assertThat(idOf(second)).isEqualTo(id);
        assertThat(records(second).get(0).status()).isEqualTo(SegmentStatus.PENDING);
    }

    @Test
    void plan_tenShortParagraphsOnBalanced_plansTwoChunksAndNoOversized() {
        final ImportedBook imported = importOk(markdownOfParagraphs(10, "A short paragraph."));

        final BookPlan plan = planOk(idOf(imported));

        assertThat(plan.chunksPerUnit()).containsEntry("Book.md", 2);
        assertThat(plan.oversizedSegmentIds()).isEmpty();
    }

    @Test
    void plan_sixThousandCharacterParagraph_listsItAsOversized() {
        final Path book = TestBooks.markdown(
                tempDir.resolve("Book.md"),
                "First short paragraph.\n\n" + "word ".repeat(1200).trim() + "\n");

        final BookPlan plan = planOk(idOf(importOk(book)));

        assertThat(plan.oversizedSegmentIds()).containsExactly("Book.md:1");
    }

    @Test
    void plan_markdownFrontmatter_plansAuxiliaryChunkOnlyWhenSwitchedOn() {
        final Path book = TestBooks.markdown(
                tempDir.resolve("Book.md"), "---\ntitle: The Lighthouse\n---\n\nIt was a dark night.\n");
        final ImportedBook imported = importOk(book);
        final String id = idOf(imported);

        assertThat(planOk(id).chunksPerUnit().getOrDefault(Unit.AUXILIARY_ID, 0))
                .isZero();
        final BookBrief on = withSwitches(briefOf(imported), new AlsoTranslate(true, true, true, true));
        assertThat(service.updateBrief(id, on).isOk()).isTrue();
        assertThat(planOk(id).chunksPerUnit()).containsEntry(Unit.AUXILIARY_ID, 1);
    }

    @Test
    void plan_epubAuxiliaryUnit_plansOneChunkUnlessMetadataAndNavigationAreOff() {
        final ImportedBook imported = importOk(TestBooks.epub(tempDir.resolve("book.epub"), TWO_CHAPTERS, "en"));
        final String id = idOf(imported);

        assertThat(planOk(id).chunksPerUnit()).containsEntry(Unit.AUXILIARY_ID, 1);
        final BookBrief off = withSwitches(briefOf(imported), new AlsoTranslate(false, true, false, false));
        service.updateBrief(id, off);
        assertThat(planOk(id).chunksPerUnit().getOrDefault(Unit.AUXILIARY_ID, 0))
                .isZero();
    }

    @Test
    void updateBrief_newDial_isStoredAndChangesTheChunkCount() {
        final ImportedBook imported = importOk(markdownOfParagraphs(10, "A short paragraph."));
        final BookBrief fast = new BookBrief(
                "en",
                "uk",
                null,
                briefOf(imported).register(),
                null,
                null,
                briefOf(imported).names(),
                briefOf(imported).foreignPassages(),
                briefOf(imported).footnotes(),
                briefOf(imported).units(),
                briefOf(imported).balance(),
                briefOf(imported).alsoTranslate(),
                QualityDial.FAST);

        final Result<Project> updated = service.updateBrief(idOf(imported), fast);

        assertThat(updated.isOk()).isTrue();
        assertThat(storedProject(imported).brief().dial()).isEqualTo(QualityDial.FAST);
        assertThat(planOk(idOf(imported)).chunksPerUnit()).containsEntry("Book.md", 2);
    }

    @Test
    void updateBriefAndPlan_unknownProject_answerValidation() {
        final BookBrief brief = BookBrief.defaults("en");

        assertThat(codeOf(service.updateBrief("missing", brief))).isEqualTo(ErrorCode.validation);
        assertThat(codeOf(service.plan("missing"))).isEqualTo(ErrorCode.validation);
        assertThat(codeOf(service.close("missing"))).isEqualTo(ErrorCode.validation);
    }

    @Test
    void close_openProject_makesAFollowingPlanAnswerValidation() {
        final String id = idOf(importOk(markdownOfParagraphs(3, "A short paragraph.")));

        assertThat(service.close(id).isOk()).isTrue();

        assertThat(codeOf(service.plan(id))).isEqualTo(ErrorCode.validation);
    }

    private ImportedBook importOk(final Path book) {
        final Result<ImportedBook> result = service.importBook(book);
        assertThat(result.isOk()).as("import result %s", result.error()).isTrue();
        return Objects.requireNonNull(result.data());
    }

    private static String idOf(final ImportedBook imported) {
        return Objects.requireNonNull(imported.projectId());
    }

    private static BookBrief briefOf(final ImportedBook imported) {
        return Objects.requireNonNull(imported.brief());
    }

    private static ErrorCode codeOf(final Result<?> result) {
        return Objects.requireNonNull(result.error()).code();
    }

    private BookPlan planOk(final String projectId) {
        final Result<BookPlan> result = service.plan(projectId);
        assertThat(result.isOk()).as("plan result %s", result.error()).isTrue();
        return Objects.requireNonNull(result.data());
    }

    private Path markdownOfParagraphs(final int count, final String paragraph) {
        final String text = String.join(
                "\n\n", IntStream.range(0, count).mapToObj(index -> paragraph).toList());
        return TestBooks.markdown(tempDir.resolve("Book.md"), text + "\n");
    }

    private Project storedProject(final ImportedBook imported) {
        return Objects.requireNonNull(projects.find(idOf(imported)).data()).orElseThrow();
    }

    private List<SegmentRecord> records(final ImportedBook imported) {
        return Objects.requireNonNull(segments.all(idOf(imported)).data());
    }

    private int segmentCount(final Path book) {
        final Document document = Objects.requireNonNull(documents.open(book).data());
        final int count = document.units().stream()
                .mapToInt(unit -> unit.segments().size())
                .sum();
        documents.close(document);
        return count;
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
