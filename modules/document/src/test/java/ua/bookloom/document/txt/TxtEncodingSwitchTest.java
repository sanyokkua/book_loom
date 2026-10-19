package ua.bookloom.document.txt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.document.fixture.TargetedDocuments;

/** A TXT export the source encoding cannot hold is written whole as UTF-8, never refused (task 5.5). */
class TxtEncodingSwitchTest {

    @TempDir
    private Path tempDir;

    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private static final String RUSSIAN_FIRST =
            "Он бегал по улице, крича во весь голос, и никто не обращал на него внимания.\n"
                    + "Дождь шёл третий день подряд, и город казался серым насквозь и навсегда.";
    private static final String RUSSIAN_SECOND =
            "Вечером она вышла на балкон и долго смотрела, как гаснут окна напротив.\n"
                    + "Утром всё повторилось снова, только ветер переменился и стало холоднее.";
    private static final String RUSSIAN = RUSSIAN_FIRST + "\n\n" + RUSSIAN_SECOND + "\n";

    private static final String FRENCH_FIRST =
            "Le café du coin était fermé ce matin-là, et personne ne savait pourquoi.\n"
                    + "Les élèves attendaient devant la porte, sous la pluie fine et froide.";
    private static final String FRENCH_SECOND =
            "Ensuite le maître est arrivé, très pressé, avec son grand parapluie déchiré.";
    private static final String FRENCH = FRENCH_FIRST + "\n\n" + FRENCH_SECOND + "\n";

    private final OpenTxtRegistry registry = new OpenTxtRegistry();

    // WHEN a windows-1251 file is exported with a target the code page cannot hold, THEN the output decodes as UTF-8
    // to the untouched first paragraph plus the target, has no byte-order mark, and re-opens as UTF-8.
    @Test
    void write_windows1251SourceWithCjkTarget_writesUtf8WithoutMarkAndReopensAsUtf8() {
        final Document document = TargetedDocuments.withTarget(open(RUSSIAN, WINDOWS_1251), 1, "車.");

        final byte[] output = bytesOf(new TxtWriter(registry).write(document, tempDir.resolve("out.txt"), "uk"));

        assertThat(new String(output, StandardCharsets.UTF_8)).isEqualTo(RUSSIAN_FIRST + "\n\n車.\n");
        assertThat(new String(output, StandardCharsets.UTF_8)).doesNotStartWith("\uFEFF");
        final Document reopened = new TxtReader(new OpenTxtRegistry()).read(tempDir.resolve("out.txt"));
        assertThat(reopened.charset()).isEqualTo("UTF-8");
    }

    // WHEN a Latin-1 family file is exported with a Cyrillic target, THEN the export succeeds and the output decodes
    // as UTF-8 to the source's own characters plus the target.
    @Test
    void write_windows1252SourceWithCyrillicTarget_isWrittenAsUtf8NotRefused() {
        final Document document = TargetedDocuments.withTarget(open(FRENCH, WINDOWS_1252), 1, "Привіт.");

        final byte[] output = bytesOf(new TxtWriter(registry).write(document, tempDir.resolve("out.txt"), "uk"));

        assertThat(new String(output, StandardCharsets.UTF_8)).isEqualTo(FRENCH_FIRST + "\n\nПривіт.\n");
    }

    // WHEN the target fits the source code page, THEN the file stays in it and the bytes before the second
    // paragraph are unchanged.
    @Test
    void write_representableTarget_keepsTheSourceEncodingAndEarlierBytes() {
        final Document document = TargetedDocuments.withTarget(open(RUSSIAN, WINDOWS_1251), 1, "Два — три.");

        final byte[] output = bytesOf(new TxtWriter(registry).write(document, tempDir.resolve("out.txt"), "uk"));

        assertThat(new String(output, WINDOWS_1251)).isEqualTo(RUSSIAN_FIRST + "\n\nДва — три.\n");
        assertThat(output).startsWith(RUSSIAN_FIRST.getBytes(WINDOWS_1251));
    }

    private Document open(String text, Charset charset) {
        final Path file = tempDir.resolve("notes.txt");
        try {
            Files.write(file, text.getBytes(charset));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        final Document document = new TxtReader(registry).read(file);
        return document;
    }

    private static byte[] bytesOf(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
