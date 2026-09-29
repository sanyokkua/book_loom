package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.withTarget;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.persistence.PersistenceModule;

/** Proves the public engine translates every supported generated book through the real document module. */
class TranslationEngineEndToEndTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PLACEHOLDER = Pattern.compile("⟦g\\d+⟧");

    @TempDir
    private Path tempDir;

    // Breaking EPUB masking, metadata propagation, or checked export would change these literal output facts.
    @Test
    void run_generatedEpub_completesWithMarkupMetadataAndEqualSegments() {
        final Path source =
                TestBooks.epub(tempDir.resolve("Book.epub"), List.of(List.of("Tom &amp; <i>Jerry</i> ran.")), "en");
        final Path destination = tempDir.resolve("Book.uk.epub");

        final ExportedBook exported = translate(source, destination);
        final String chapter = zipEntry(destination, "OEBPS/ch0.xhtml");
        final String packageDocument = zipEntry(destination, "OEBPS/content.opf");

        assertCompleted(exported, destination, BookFormat.EPUB);
        assertThat(chapter).contains("TOM &amp; <i>JERRY</i> RAN.");
        assertThat(packageDocument).contains("<dc:language>uk</dc:language>");
        assertCheckedExport(source, destination);
    }

    // Breaking FB2 tree restoration or target-language propagation would change these literal output facts.
    @Test
    void run_generatedFb2_completesWithMarkupMetadataAndEqualSegments() {
        final Path source = TestBooks.fb2(tempDir.resolve("Book.fb2"), List.of("Tom <emphasis>ran</emphasis>."), "en");
        final Path destination = tempDir.resolve("Book.uk.fb2");

        final ExportedBook exported = translate(source, destination);
        final String output = read(destination);

        assertCompleted(exported, destination, BookFormat.FB2);
        assertThat(output).contains("TOM <emphasis>RAN</emphasis>.");
        assertThat(output).contains("<lang>uk</lang>");
        assertCheckedExport(source, destination);
    }

    // Treating zipped FB2 as a different output type would lose either its markup, metadata, or segment count.
    @Test
    void run_generatedZippedFb2_completesWithMarkupMetadataAndEqualSegments() {
        final Path source =
                TestBooks.zippedFb2(tempDir.resolve("Book.fb2.zip"), List.of("Tom <emphasis>ran</emphasis>."), "en");
        final Path destination = tempDir.resolve("Book.uk.fb2.zip");

        final ExportedBook exported = translate(source, destination);
        final String output = zipEntry(destination, "book.fb2");

        assertCompleted(exported, destination, BookFormat.FB2);
        assertThat(output).contains("TOM <emphasis>RAN</emphasis>.");
        assertThat(output).contains("<lang>uk</lang>");
        assertCheckedExport(source, destination);
    }

    // Breaking Markdown placeholder restoration would either remove the emphasis or fail to uppercase its text.
    @Test
    void run_generatedMarkdown_completesWithMarkupAndEqualSegments() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the *old* door.", "en");
        final Path destination = tempDir.resolve("Book.uk.md");

        final ExportedBook exported = translate(source, destination);

        assertCompleted(exported, destination, BookFormat.MARKDOWN);
        assertThat(read(destination)).contains("HE OPENED THE *OLD* DOOR.");
        assertCheckedExport(source, destination);
    }

    // Bypassing the real TXT reader or writer would fail this literal whole-file translation.
    @Test
    void run_generatedTxt_completesWithUppercaseAndEqualSegments() {
        final Path source = TestBooks.txt(tempDir.resolve("Book.txt"), "Hello world.\n\nSecond paragraph.");
        final Path destination = tempDir.resolve("Book.uk.txt");

        final ExportedBook exported = translate(source, destination);

        assertCompleted(exported, destination, BookFormat.TXT);
        assertThat(read(destination)).isEqualTo("HELLO WORLD.\n\nSECOND PARAGRAPH.");
        assertCheckedExport(source, destination);
    }

    private ExportedBook translate(final Path source, final Path destination) {
        final Injector injector = Guice.createInjector(
                new DocumentModule(), new LlmModule(), new PersistenceModule(), new PipelineModule());
        final ProjectService projects = injector.getInstance(ProjectService.class);
        final ImportedBook imported = dataOf(projects.importBook(source));
        final String projectId = Objects.requireNonNull(imported.projectId(), "project id");
        dataOf(projects.updateBrief(projectId, withTarget(Objects.requireNonNull(imported.brief()), "uk")));
        final TranslationJob job = dataOf(injector.getInstance(TranslationEngine.class)
                .newJob(new RunRequest(projectId, ReviewMode.UNATTENDED), jsonUppercaseModel()));
        final JobReport report = dataOf(job.run());
        assertThat(destination).doesNotExist();
        final ExportJob export = dataOf(injector.getInstance(ExportService.class)
                .newExport(new ExportRequest(projectId, destination, false, Set.of(), false), null));
        return new ExportedBook(report, dataOf(export.run()));
    }

    private record ExportedBook(JobReport report, ExportReport written) {}

    private static ChatModel jsonUppercaseModel() {
        return request -> {
            try {
                final String target = uppercasePreservingPlaceholders(
                        sourceText(request.messages().get(1).content()));
                final ObjectNode reply = MAPPER.createObjectNode();
                reply.put("target", target);
                return Result.ok(new ChatResponse(MAPPER.writeValueAsString(reply), FinishReason.STOP));
            } catch (JsonProcessingException cause) {
                throw new AssertionError("could not parse draft prompt source", cause);
            }
        };
    }

    private static String sourceText(final String userMessage) {
        return userMessage
                .lines()
                .dropWhile(line -> !line.equals("<Text>"))
                .dropWhile(line -> line.equals("<Text>"))
                .takeWhile(line -> !line.equals("</Text>"))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String uppercasePreservingPlaceholders(final String source) {
        final Matcher matcher = PLACEHOLDER.matcher(source);
        final StringBuilder target = new StringBuilder(source.length());
        int index = 0;
        while (matcher.find()) {
            target.append(source.substring(index, matcher.start()).toUpperCase(Locale.ROOT))
                    .append(matcher.group());
            index = matcher.end();
        }
        return target.append(source.substring(index).toUpperCase(Locale.ROOT)).toString();
    }

    private void assertCheckedExport(final Path source, final Path destination) {
        assertThat(Files.notExists(destination.resolveSibling("." + destination.getFileName())))
                .isTrue();
        final Injector injector = Guice.createInjector(
                new DocumentModule(), new LlmModule(), new PersistenceModule(), new PipelineModule());
        final DocumentPort documents = injector.getInstance(DocumentPort.class);
        final Document sourceDocument = dataOf(documents.open(source));
        try {
            final Document outputDocument = dataOf(documents.open(destination));
            try {
                assertThat(segmentCount(outputDocument)).isEqualTo(segmentCount(sourceDocument));
            } finally {
                assertThat(dataOf(documents.close(outputDocument))).isTrue();
            }
        } finally {
            assertThat(dataOf(documents.close(sourceDocument))).isTrue();
        }
    }

    private static void assertCompleted(
            final ExportedBook exported, final Path destination, final BookFormat expectedFormat) {
        assertThat(exported.report().end()).isEqualTo(JobState.COMPLETED);
        assertThat(exported.report().format()).isEqualTo(expectedFormat);
        assertThat(exported.report().error()).isNull();
        assertThat(exported.written().destination()).isEqualTo(destination);
        assertThat(Files.exists(destination)).isTrue();
    }

    private static int segmentCount(final Document document) {
        return document.units().stream()
                .mapToInt(unit -> unit.segments().size())
                .sum();
    }

    private static String zipEntry(final Path archive, final String entryName) {
        try (ZipFile zip = new ZipFile(archive.toString(), StandardCharsets.UTF_8)) {
            try (InputStream input = zip.getInputStream(Objects.requireNonNull(zip.getEntry(entryName), entryName))) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception cause) {
            throw new AssertionError("could not read generated archive", cause);
        }
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (Exception cause) {
            throw new AssertionError("could not read generated book", cause);
        }
    }

    private static <T> T dataOf(final Result<T> result) {
        assertThat(result.isOk()).isTrue();
        return Objects.requireNonNull(result.data(), "result data");
    }
}
