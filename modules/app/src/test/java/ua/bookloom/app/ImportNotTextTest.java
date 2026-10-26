package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ImportVerdict;
import ua.bookloom.ui.state.ImportVerdicts;
import ua.bookloom.ui.state.ImportViewModel;

/**
 * Files whose content is not what their name promises, imported in the real window: binary bytes or an EPUB named
 * {@code .txt} are no text file, and an empty EPUB or FB2, or an FB2 with no body, has no text to translate; none of
 * them leaves a project, while a UTF-16 text with its byte-order mark is still read.
 */
// `slow`: imports in the real window.
@Tag("slow")
class ImportNotTextTest extends WorkspaceTestBase {

    private static final String NO_BODY_FB2 = """
            <?xml version="1.0" encoding="UTF-8"?><FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">\
            <description><title-info><genre>prose</genre><book-title>Empty</book-title><lang>en</lang></title-info>\
            </description></FictionBook>""";

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "binary|noise.txt|noise.txt is not a text file.",
                "epub|book.txt|book.txt is not a text file.",
                "empty|empty.epub|This book has no text to translate.",
                "empty|empty.fb2|This book has no text to translate.",
                "nobody|nobody.fb2|This book has no text to translate."
            })
    void import_contentThatIsNoBookText_isRefusedWithItsOwnLineAndStoresNothing(
            final String content, final String name, final String line) throws Exception {
        final Path file = write(content, booksDir.resolve(name));
        startStoredWindow();

        onFx(() -> injector.getInstance(ImportViewModel.class).open(file));
        waitUntil(() -> !injector.getInstance(ImportViewModel.class).opening().get());

        final String shown = onFx(() -> ImportVerdicts.of(
                        injector.getInstance(Messages.class),
                        injector.getInstance(ImportViewModel.class).state().get())
                .map(ImportVerdict::text)
                .orElse(""));
        assertThat(shown).isEqualTo(line);
        assertThat(onFx(() -> shell.root().lookup("#import-verdict-choose"))).isNotNull();
        assertThat(Objects.requireNonNull(injector.getInstance(ProjectService.class).list().data())).isEmpty();
    }

    @Test
    void import_utf16TextWithItsByteOrderMark_isRead() throws Exception {
        final byte[] text = "It was a dark night and the rain fell.\n".getBytes(StandardCharsets.UTF_16LE);
        final byte[] marked = new byte[text.length + 2];
        marked[0] = (byte) 0xFF;
        marked[1] = (byte) 0xFE;
        System.arraycopy(text, 0, marked, 2, text.length);
        final Path file = Files.write(booksDir.resolve("wide.txt"), marked);
        startStoredWindow();

        openBook(file, 1);
    }

    private Path write(final String content, final Path file) throws Exception {
        return switch (content) {
            case "binary" -> Files.write(file, binary());
            case "epub" -> TestEpub.write(
                    file,
                    "Renamed",
                    "    <item id=\"c\" href=\"c.xhtml\" media-type=\"application/xhtml+xml\"/>\n",
                    "<itemref idref=\"c\"/>",
                    Map.of("c.xhtml", TestEpub.chapter("C", "<p>Text.</p>")));
            case "nobody" -> Files.writeString(file, NO_BODY_FB2, StandardCharsets.UTF_8);
            default -> Files.write(file, new byte[0]);
        };
    }

    // Every byte value in turn: NUL bytes and control characters, as an executable or an image holds them.
    private static byte[] binary() {
        final byte[] bytes = new byte[4096];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }
}
