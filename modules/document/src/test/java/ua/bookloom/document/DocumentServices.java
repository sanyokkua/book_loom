package ua.bookloom.document;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.document.epub.EpubTestFactory;
import ua.bookloom.document.fb2.Fb2Reader;
import ua.bookloom.document.fb2.Fb2Writer;
import ua.bookloom.document.fb2.OpenFb2Registry;
import ua.bookloom.document.md.MarkdownReader;
import ua.bookloom.document.md.MarkdownWriter;
import ua.bookloom.document.md.OpenMarkdownRegistry;
import ua.bookloom.document.txt.OpenTxtRegistry;
import ua.bookloom.document.txt.TxtReader;
import ua.bookloom.document.txt.TxtWriter;

/**
 * Builds a {@link DocumentService} whose four reader/writer pairs each share one fresh in-memory registry — the
 * arrangement production Guice produces, and the one a write test needs so a document opened by a reader can be
 * found again by its writer. Test-only: no production code depends on this class.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DocumentServices {

    /**
     * A fully wired service over fresh registries.
     *
     * @return a service ready to open and write any of the four formats
     */
    public static DocumentService newService() {
        final EpubTestFactory.ReaderAndWriter epub = EpubTestFactory.newSharedPair();
        final OpenFb2Registry fb2 = new OpenFb2Registry();
        final OpenMarkdownRegistry markdown = new OpenMarkdownRegistry();
        final OpenTxtRegistry txt = new OpenTxtRegistry();
        return new DocumentService(
                epub.reader(),
                epub.writer(),
                new Fb2Reader(fb2),
                new Fb2Writer(fb2),
                new MarkdownReader(markdown),
                new MarkdownWriter(markdown),
                new TxtReader(txt),
                new TxtWriter(txt));
    }
}
