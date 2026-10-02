package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;

/**
 * Lists and verse keep one line per source line, from the gemma4:e4b run on the earth-gravity fixture: a TXT list or
 * stanza reached the model without any line mark and came back merged into one line, and a Markdown stanza whose
 * model wrote a space after a hard-break token ({@code землю,⟦g0⟧ він}) restored as {@code землю,\ він} and was
 * refused, leaving the stanza in English.
 */
class LineStructureRestoreTest {

    private static final String TXT_LIST = "• Mass describes how much matter an object contains.\n"
            + "• Weight describes the gravitational force acting on that mass.";
    private static final String MD_STANZA = "A stone let go will find the ground,\\\nit never asks the way;\\\n"
            + "the Earth is calling all around\\\nand every stone obeys.";

    @TempDir
    private Path tempDir;

    private DocumentPort documents;

    @BeforeEach
    void setUp() {
        documents = Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    }

    @Test
    void open_txtList_marksEachLineEndWithALineBreakToken() throws IOException {
        final Segment segment = firstSegment("book.txt", TXT_LIST + "\n");

        assertThat(segment.masked())
                .isEqualTo("• Mass describes how much matter an object contains.⟦g0⟧\n"
                        + "• Weight describes the gravitational force acting on that mass.");
        assertThat(segment.lineBreakTokens()).containsExactly("⟦g0⟧");
    }

    // A hard-wrapped prose paragraph is reflowed by the translation; marking its wrap points would pin the model's
    // sentences to the source's line lengths.
    @Test
    void open_txtHardWrappedProse_carriesNoLineBreakToken() throws IOException {
        final String prose = """
                Gravity is one of the fundamental interactions in nature, and Dr. Vance liked
                to begin every lecture with that sentence, and then with a pause long enough for
                somebody at the back to drop a pen.
                """;

        final Segment segment = firstSegment("book.txt", prose);

        assertThat(segment.masked()).isEqualTo(prose.strip());
        assertThat(segment.lineBreakTokens()).isEmpty();
    }

    @Test
    void unmask_txtListWithASpaceAfterTheLineBreakToken_restoresTheLineEnd() throws IOException {
        final Segment segment = firstSegment("book.txt", TXT_LIST + "\n");

        final Result<String> restored = documents.unmask(
                BookFormat.TXT,
                segment,
                "• Маса описує, скільки речовини містить тіло.⟦g0⟧ • Вага описує силу тяжіння.");

        assertThat(restored.data())
                .isEqualTo("• Маса описує, скільки речовини містить тіло.\n• Вага описує силу тяжіння.");
    }

    @Test
    void unmask_txtListMergedIntoOneLine_isRefused() throws IOException {
        final Segment segment = firstSegment("book.txt", TXT_LIST + "\n");

        final Result<String> restored = documents.unmask(
                BookFormat.TXT, segment, "• Маса описує, скільки речовини містить тіло. • Вага описує силу тяжіння.");

        assertThat(Objects.requireNonNull(restored.error()).code()).isEqualTo(ErrorCode.validation);
    }

    @Test
    void unmask_markdownStanzaWithSpacesAfterItsHardBreaks_restoresEveryLine() throws IOException {
        final Segment segment = firstSegment("book.md", MD_STANZA + "\n");

        final Result<String> restored = documents.unmask(
                BookFormat.MARKDOWN,
                segment,
                "Камінь знайде землю,⟦g0⟧ він не питає шляху;⟦g1⟧ Земля кличе довкола⟦g2⟧ і кожен камінь слухається.");

        assertThat(restored.data())
                .isEqualTo("Камінь знайде землю,\\\nвін не питає шляху;\\\nЗемля кличе довкола\\\n"
                        + "і кожен камінь слухається.");
    }

    @Test
    void unmask_markdownStanzaWithTheNewlineBeforeItsHardBreak_restoresEveryLine() throws IOException {
        final Segment segment = firstSegment("book.md", MD_STANZA + "\n");

        final Result<String> restored = documents.unmask(
                BookFormat.MARKDOWN,
                segment,
                "Камінь знайде землю,\n⟦g0⟧він не питає шляху; ⟦g1⟧\nЗемля кличе довкола⟦g2⟧\nі кожен камінь слухається.");

        assertThat(restored.data())
                .isEqualTo("Камінь знайде землю,\\\nвін не питає шляху;\\\nЗемля кличе довкола\\\n"
                        + "і кожен камінь слухається.");
    }

    private Segment firstSegment(final String name, final String content) throws IOException {
        final Path book = tempDir.resolve(name);
        Files.writeString(book, content, StandardCharsets.UTF_8);
        return Objects.requireNonNull(documents.open(book).data())
                .units()
                .getFirst()
                .segments()
                .getFirst();
    }
}
