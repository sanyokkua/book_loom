package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/** The diagnostic bundle: the detailed log's files and {@code session.json}, nothing else, written where chosen. */
class DiagnosticBundleTest {

    @TempDir
    private Path tempDir;

    @Test
    void write_detailedLogFiles_bundlesEveryTraceFileAndTheSessionButNotTheOrdinaryLog() throws IOException {
        final Path logs = Files.createDirectory(tempDir.resolve("logs"));
        Files.writeString(logs.resolve("bookloom-trace.log"), "active trace\n");
        Files.writeString(logs.resolve("bookloom-trace.1.log.gz"), "archive one");
        Files.writeString(logs.resolve("bookloom.log"), "ordinary\n");
        final Path target = tempDir.resolve("bundle.zip");

        final Result<Integer> written = DiagnosticBundle.write(target, logs, "{}\n");

        assertThat(written.data()).isEqualTo(2);
        assertThat(entries(target))
                .containsExactly("bookloom-trace.1.log.gz", "bookloom-trace.log", DiagnosticBundle.SESSION_ENTRY);
        assertThat(entry(target, "bookloom-trace.log")).isEqualTo("active trace\n");
    }

    @Test
    void write_noDetailedLog_bundlesTheOrdinaryLogInstead() throws IOException {
        final Path logs = Files.createDirectory(tempDir.resolve("logs"));
        Files.writeString(logs.resolve("bookloom.log"), "ordinary\n");
        final Path target = tempDir.resolve("bundle.zip");

        DiagnosticBundle.write(target, logs, "{}\n");

        assertThat(entries(target)).containsExactly("bookloom.log", DiagnosticBundle.SESSION_ENTRY);
    }

    @Test
    void write_missingLogFolder_failsWithAnInternalErrorAndLeavesNoFile() {
        final Path target = tempDir.resolve("bundle.zip");

        final Result<Integer> written = DiagnosticBundle.write(target, tempDir.resolve("absent"), "{}\n");

        assertThat(written.error()).isNotNull().extracting("code").isEqualTo(ErrorCode.internal);
        assertThat(target).doesNotExist();
    }

    @Test
    void sessionJson_factsWithQuotesAndBreaks_isEscapedJsonWithTheSessionNested() {
        final Map<String, String> about = new LinkedHashMap<>();
        about.put("version", "dev");
        about.put("os", "Mac \"OS\"\nX");
        final Map<String, String> session = new LinkedHashMap<>();
        session.put("book", "Kobzar.fb2");

        assertThat(DiagnosticBundle.sessionJson(about, session)).isEqualTo("""
                        {
                          "version": "dev",
                          "os": "Mac \\"OS\\"\\nX",
                          "session": {
                            "book": "Kobzar.fb2"
                          }
                        }
                        """);
    }

    private static List<String> entries(final Path zip) throws IOException {
        final List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry next = in.getNextEntry(); next != null; next = in.getNextEntry()) {
                names.add(next.getName());
            }
        }
        return names;
    }

    private static String entry(final Path zip, final String name) throws IOException {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry next = in.getNextEntry(); next != null; next = in.getNextEntry()) {
                if (next.getName().equals(name)) {
                    return new String(((InputStream) in).readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return "";
    }
}
