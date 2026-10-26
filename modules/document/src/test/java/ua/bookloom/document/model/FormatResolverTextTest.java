package ua.bookloom.document.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A {@code .txt} or {@code .md} name is believed only over text: an archive, a NUL byte or control noise is refused. */
class FormatResolverTextTest {

    @TempDir
    private Path dir;

    @ParameterizedTest
    @CsvSource({
        // plain ASCII with a tab and line breaks
        "notes.txt,48656c6c6f09776f726c640d0a0a4279650a,true",
        // Cyrillic in UTF-8 and in windows-1251
        "notes.txt,d09fd180d0b8d0b2d196d1820a,true",
        "notes.md,cff0e8e2b3f20a,true",
        // UTF-16 LE and BE with their byte-order marks hold NUL bytes and are still text
        "notes.txt,fffe48006900,true",
        "notes.txt,feff00480069,true",
        // a zip signature, a NUL byte, and mostly control bytes
        "notes.txt,504b030414000000,false",
        "notes.md,48650069,false",
        "notes.txt,0102030405060708410a,false",
        // an empty file is text with no words; the inspection decides it has nothing to translate
        "notes.txt,'',true"
    })
    void probe_textNamedFile_isBelievedOnlyOverText(final String name, final String hex, final boolean text)
            throws Exception {
        final Path file = Files.write(dir.resolve(name), HexFormat.of().parseHex(hex));

        assertThat(FormatResolver.probe(file).isPresent()).isEqualTo(text);
    }
}
