package ua.bookloom.pipeline.setup;

import java.util.Locale;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.project.Project;
import ua.bookloom.pipeline.SourceLanguage;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.prompt.CallFrame;

/**
 * The stored project a setup call is about, with its open book.
 *
 * @param project the stored project
 * @param document the open book
 * @param target the brief's target language; never blank
 * @param index the book's index, or null when none was built
 */
record SetupBook(
        Project project,
        Document document,
        String target,
        @Nullable BookIndex index) {

    private static final String FB2_ZIP = ".fb2.zip";

    /** The frame every setup call of this book is built in. */
    CallFrame frame() {
        return CallFrame.forBook(project.brief(), document, index);
    }

    /** The language the code reads the book in: the chosen source, else the one it declares; null when unknown. */
    @Nullable
    String languageOf() {
        return SourceLanguage.forAnalysis(project.brief(), document);
    }

    /** The source file's name without its extension. */
    String fileStem() {
        final String fileName = project.source().getFileName().toString();
        if (fileName.toLowerCase(Locale.ROOT).endsWith(FB2_ZIP)) {
            return fileName.substring(0, fileName.length() - FB2_ZIP.length());
        }
        final int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
