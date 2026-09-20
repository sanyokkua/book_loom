package ua.bookloom.document;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** File fixtures shared by {@link DocumentServiceTest}'s EPUB and port-boundary cases. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DocumentServiceTestFiles {

    static Map<String, String> entries(String... namesAndContents) {
        final Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < namesAndContents.length; i += 2) {
            entries.put(namesAndContents[i], namesAndContents[i + 1]);
        }
        return entries;
    }

    static void zip(Path file, Map<String, String> entries) {
        try (OutputStream out = Files.newOutputStream(file);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            for (final Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void writeBytes(Path file, byte[] content) {
        try {
            Files.write(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
