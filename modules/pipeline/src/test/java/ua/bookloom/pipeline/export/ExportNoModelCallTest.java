package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.export.ExportJobFixture.MAPPER;
import static ua.bookloom.pipeline.export.ExportJobFixture.bodySegments;
import static ua.bookloom.pipeline.export.ExportJobFixture.ok;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.llm.pseudo.PseudoChatModel;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.WireMockProvider;

/**
 * Pipeline e2e at the HTTP seam: a book translated with the pseudo model is written with the last check switched on
 * while the project's provider is a real client pointed at a stub server, and the server is never called.
 */
class ExportNoModelCallTest {

    private static final String ANY_REPLY = "{\"items\":[{\"id\":\"1\",\"target\":\"Так.\"}]}";

    @TempDir
    private Path tempDir;

    // IF a provider is set, THEN the export still writes the book and sends the provider no request.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_providerSet_sendsNoRequestAndWritesTheBook(final ProviderKind kind) {
        final ExportJobFixture fixture = new ExportJobFixture();
        final Path source = TestBooks.epubAtRoot(
                tempDir.resolve("Mini.epub"),
                List.of("ch01.xhtml", "ch02.xhtml"),
                List.of(
                        List.of("The lamp burned low.", "Rain fell on the roof."),
                        List.of("Morning came late.", "The road was empty.")));
        final String id = fixture.importBook(source, "en");
        ok(ok(fixture.engine().newJob(new RunRequest(id, ReviewMode.UNATTENDED), new PseudoChatModel(MAPPER)))
                .run());
        final Path destination = tempDir.resolve("Mini.uk.epub");

        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubResponder(ignored -> ANY_REPLY);

            ok(ok(fixture.service().newExport(new ExportRequest(id, destination, false, Set.of())))
                    .run());

            assertThat(provider.servedInOrder())
                    .as("requests the stub server recorded during the export")
                    .isEmpty();
        }
        assertThat(destination).isRegularFile();
        assertThat(bodySegments(ok(fixture.documents().open(destination))))
                .hasSameSizeAs(bodySegments(ok(fixture.documents().open(source))));
    }
}
