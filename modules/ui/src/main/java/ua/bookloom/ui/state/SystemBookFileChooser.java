package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/** The platform's own open dialog for a book file of any of the four formats, owned by the window that asked. */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SystemBookFileChooser implements BookFileChooser {

    private final Messages messages;

    @Override
    public Optional<Path> chooseToOpen(final @Nullable Window owner) {
        log.debug("opening the book open dialog");
        final FileChooser chooser = new FileChooser();
        chooser.setTitle(messages.get(MessageKey.DIALOG_FIND_BOOK_CHOOSER_TITLE));
        chooser.getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter("EPUB, FB2, Markdown, TXT", "*.epub", "*.fb2", "*.md", "*.txt"),
                        new FileChooser.ExtensionFilter("*", "*.*"));
        final File picked = chooser.showOpenDialog(owner);
        log.debug("the book open dialog answered: a file was picked {}", picked != null);
        return Optional.ofNullable(picked).map(File::toPath);
    }
}
