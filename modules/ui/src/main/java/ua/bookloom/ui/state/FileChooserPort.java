package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * Asks the person for a file to read or write, behind a seam so a test can answer without a real file dialog opening
 * on the machine that runs it.
 */
public interface FileChooserPort {

    /** Which files a dialog lists, and the heading it has. */
    enum FileKind {
        /** A book of any of the four formats, when a project's book has moved. */
        BOOK(MessageKey.DIALOG_FIND_BOOK_CHOOSER_TITLE),
        /** A term table's CSV file, for its Import and Export. */
        CSV(null),
        /** Any file, listed unfiltered: the translated book, the diagnostic bundle, a backup. */
        ANY(null);

        private final @Nullable MessageKey title;

        FileKind(final @Nullable MessageKey title) {
            this.title = title;
        }

        /**
         * The dialog's heading.
         *
         * @return the heading's key, or {@code null} for the platform's own
         */
        public @Nullable MessageKey title() {
            return title;
        }

        /**
         * The filters the dialog offers, the first chosen.
         *
         * @return never null; empty for an unfiltered dialog
         */
        public List<ExtensionFilter> filters() {
            return switch (this) {
                case BOOK ->
                    List.of(
                            new ExtensionFilter("EPUB, FB2, Markdown, TXT", "*.epub", "*.fb2", "*.md", "*.txt"),
                            new ExtensionFilter("*", "*.*"));
                case CSV -> List.of(new ExtensionFilter("CSV", "*.csv"));
                case ANY -> List.of();
            };
        }
    }

    /**
     * Shows an open dialog. FX thread only.
     *
     * @param owner the window the dialog belongs to, so that it opens over the app; {@code null} when none is showing
     * @param kind which files it lists
     * @return the file the person picked, or empty if the dialog was cancelled
     */
    Optional<Path> chooseToOpen(@Nullable Window owner, FileKind kind);

    /**
     * Shows a save dialog. FX thread only.
     *
     * @param owner the window the dialog belongs to; {@code null} where the asking card is in the modal host, which is
     *     not a stage
     * @param kind which files it lists
     * @param initial the file the dialog starts at: its name, and its folder when it names an existing one
     * @return the file the person picked, or empty if the dialog was cancelled
     */
    Optional<Path> chooseToSave(@Nullable Window owner, FileKind kind, Path initial);
}
