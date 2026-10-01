package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookInspector;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.RoundTripReport;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;

/** The zero-edit round trip over the real document module, with decorators that damage the written copy. */
class RoundTripCheckTest {

    private static final String OPF = "OEBPS/content.opf";
    private static final String CHAPTER = "OEBPS/ch0.xhtml";
    private static final String IMAGE = "OEBPS/cover.png";
    private static final String IMAGE_ITEM = "<item id=\"img1\" href=\"cover.png\" media-type=\"image/png\"/>";

    @TempDir
    private Path tempDir;

    private final Injector injector = Guice.createInjector(new DocumentModule(), new PersistenceModule());

    @Test
    void roundTrip_faithfulBookWithImageAndFont_reportsEverythingPreserved() {
        final RecordingPort port = new RecordingPort(injector.getInstance(DocumentPort.class), path -> {});

        final RoundTripReport report = roundTrip(port, book("en"));

        assertThat(report.structurePreserved()).isTrue();
        assertThat(report.idsPreserved()).isTrue();
        assertThat(report.missingIds()).isEmpty();
        assertThat(report.sourceSegments()).isEqualTo(2);
        assertThat(report.copySegments()).isEqualTo(2);
    }

    @Test
    void roundTrip_writerDropsTheImageEntry_reportsIdsNotPreservedNamingTheImage() {
        final RecordingPort port =
                new RecordingPort(injector.getInstance(DocumentPort.class), RoundTripCheckTest::dropImage);

        final RoundTripReport report = roundTrip(port, book("en"));

        assertThat(report.idsPreserved()).isFalse();
        assertThat(report.missingIds()).containsExactly("img1");
        assertThat(report.structurePreserved()).isTrue();
    }

    @Test
    void roundTrip_writerDropsTheSecondParagraph_reportsStructureNotPreserved() {
        final RecordingPort port =
                new RecordingPort(injector.getInstance(DocumentPort.class), RoundTripCheckTest::dropSecondParagraph);

        final RoundTripReport report = roundTrip(port, book("en"));

        assertThat(report.structurePreserved()).isFalse();
        assertThat(report.sourceSegments()).isEqualTo(2);
        assertThat(report.copySegments()).isEqualTo(1);
        assertThat(report.idsPreserved()).isTrue();
    }

    @Test
    void roundTrip_epubDeclaringEnWithEnChapter_writesWithEnAsSourceAndTargetAndKeepsTheDeclaration() {
        final RecordingPort port = new RecordingPort(injector.getInstance(DocumentPort.class), path -> {});

        roundTrip(port, book("en"));

        assertThat(port.sourceLanguage).isEqualTo("en");
        assertThat(port.targetLanguage).isEqualTo("en");
        assertThat(port.writtenChapter).contains("<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\"");
    }

    @Test
    void roundTrip_bookDeclaringNoLanguage_writesWithNoSourceAndUndTarget() {
        final RecordingPort port = new RecordingPort(injector.getInstance(DocumentPort.class), path -> {});

        roundTrip(port, book(null));

        assertThat(port.sourceLanguage).isNull();
        assertThat(port.targetLanguage).isEqualTo("und");
    }

    @Test
    void roundTrip_afterACheck_theTemporaryDirectoryNoLongerExists() {
        final RecordingPort port = new RecordingPort(injector.getInstance(DocumentPort.class), path -> {});

        roundTrip(port, book("en"));

        assertThat(port.destination).isNotNull();
        assertThat(port.destination.getParent()).doesNotExist();
        assertThat(port.destination.getFileName()).hasToString("book.epub");
    }

    @Test
    void roundTrip_unknownProject_answersValidation() {
        final ProjectServiceImpl service = service(injector.getInstance(DocumentPort.class));

        final Result<RoundTripReport> result = service.roundTrip("missing");

        assertThat(result.isErr()).isTrue();
        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.validation);
    }

    private RoundTripReport roundTrip(final DocumentPort port, final Path book) {
        final ProjectServiceImpl service = service(port);
        final Result<ImportedBook> imported = service.importBook(book);
        final String id =
                Objects.requireNonNull(Objects.requireNonNull(imported.data()).projectId());
        final Result<RoundTripReport> result = service.roundTrip(id);
        assertThat(result.isOk()).isTrue();
        return Objects.requireNonNull(result.data());
    }

    private ProjectServiceImpl service(final DocumentPort port) {
        return new ProjectServiceImpl(
                injector.getInstance(BookInspector.class),
                port,
                injector.getInstance(ProjectRepository.class),
                injector.getInstance(SegmentRepository.class),
                new OpenProjects());
    }

    private Path book(@Nullable final String language) {
        final Path destination =
                tempDir.resolve("src" + (language == null ? "none" : language)).resolve("book.epub");
        try {
            Files.createDirectories(destination.getParent());
            try (OutputStream output = Files.newOutputStream(destination);
                    ZipOutputStream zip = new ZipOutputStream(output)) {
                put(zip, "mimetype", "application/epub+zip", ZipEntry.STORED);
                put(zip, "META-INF/container.xml", CONTAINER, ZipEntry.DEFLATED);
                put(zip, OPF, opf(language), ZipEntry.DEFLATED);
                put(zip, CHAPTER, chapter(), ZipEntry.DEFLATED);
                put(zip, IMAGE, "not-really-a-png", ZipEntry.DEFLATED);
                put(zip, "OEBPS/f.otf", "not-really-a-font", ZipEntry.DEFLATED);
            }
            return destination;
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    private static void dropImage(final Path epub) {
        rewrite(epub, name -> !name.equals(IMAGE), text -> text.replaceAll("<item [^>]*id=\"img1\"[^>]*/>", ""));
    }

    private static void dropSecondParagraph(final Path epub) {
        rewrite(epub, name -> true, text -> text.replace("<p>The rain kept falling.</p>", ""));
    }

    private static void rewrite(
            final Path epub, final java.util.function.Predicate<String> keep, final UnaryOperator<String> edit) {
        try {
            final Path tampered = epub.resolveSibling("tampered.zip");
            try (ZipFile zip = new ZipFile(epub.toFile());
                    ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tampered))) {
                for (final ZipEntry entry : Collections.list(zip.entries())) {
                    if (keep.test(entry.getName())) {
                        final byte[] raw = zip.getInputStream(entry).readAllBytes();
                        final boolean isText = entry.getName().endsWith(".opf")
                                || entry.getName().endsWith(".xhtml");
                        final String content = new String(raw, StandardCharsets.UTF_8);
                        put(out, entry.getName(), isText ? edit.apply(content) : content, entry.getMethod());
                    }
                }
            }
            Files.move(tampered, epub, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    private static String chapter() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\"><head><title>Test</title></head><body>"
                + "<p>It was a dark night.</p><p>The rain kept falling.</p></body></html>";
    }

    private static String opf(@Nullable final String language) {
        final String lang = language == null ? "" : "<dc:language>" + language + "</dc:language>";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"id\">"
                + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"id\">test</dc:identifier>"
                + "<dc:title>Test</dc:title>" + lang + "</metadata><manifest>"
                + "<item id=\"ch0\" href=\"ch0.xhtml\" media-type=\"application/xhtml+xml\"/>"
                + IMAGE_ITEM
                + "<item id=\"font1\" href=\"f.otf\" media-type=\"font/otf\"/>"
                + "</manifest><spine><itemref idref=\"ch0\"/></spine></package>";
    }

    private static final String CONTAINER = "<?xml version=\"1.0\"?>"
            + "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\" version=\"1.0\">"
            + "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>"
            + "</rootfiles></container>";

    private static void put(final ZipOutputStream zip, final String name, final String content, final int method)
            throws IOException {
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        final ZipEntry entry = new ZipEntry(name);
        entry.setMethod(method);
        if (method == ZipEntry.STORED) {
            final CRC32 crc = new CRC32();
            crc.update(bytes);
            entry.setSize(bytes.length);
            entry.setCompressedSize(bytes.length);
            entry.setCrc(crc.getValue());
        }
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    /** Delegates to the real port, records what the check passed to {@code write}, then damages the written file. */
    private static final class RecordingPort implements DocumentPort {

        private final DocumentPort delegate;
        private final Consumer<Path> damage;
        private @Nullable String sourceLanguage = "unset";
        private @Nullable String targetLanguage;
        private @Nullable Path destination;
        private @Nullable String writtenChapter;

        RecordingPort(final DocumentPort delegate, final Consumer<Path> damage) {
            this.delegate = delegate;
            this.damage = damage;
        }

        @Override
        public Result<Document> open(final Path source) {
            return delegate.open(source);
        }

        @Override
        public Result<Path> write(final Document document, final Path target, final String language) {
            return write(document, target, null, language);
        }

        @Override
        public Result<Path> write(
                final Document document, final Path target, @Nullable final String source, final String language) {
            final Result<Path> written = delegate.write(document, target, source, language);
            if (written.isOk()) {
                this.sourceLanguage = source;
                this.targetLanguage = language;
                this.destination = target;
                damage.accept(target);
                this.writtenChapter = readChapter(target);
            }
            return written;
        }

        @Override
        public Result<Boolean> close(final Document document) {
            return delegate.close(document);
        }

        @Override
        public Result<String> unmask(
                final ua.bookloom.api.document.BookFormat format,
                final ua.bookloom.api.document.Segment segment,
                final String translatedMasked) {
            return delegate.unmask(format, segment, translatedMasked);
        }

        @Override
        public Result<String> repairPlaceholders(
                final ua.bookloom.api.document.Segment segment,
                final String translatedMasked,
                final ua.bookloom.api.document.PlaceholderRepair mode) {
            return delegate.repairPlaceholders(segment, translatedMasked, mode);
        }

        private static String readChapter(final Path epub) {
            try (ZipFile zip = new ZipFile(epub.toFile())) {
                return new String(zip.getInputStream(zip.getEntry(CHAPTER)).readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException cause) {
                throw new UncheckedIOException(cause);
            }
        }
    }
}
