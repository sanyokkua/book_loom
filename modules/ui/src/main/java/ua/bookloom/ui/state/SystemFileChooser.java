package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/** The platform's own open and save dialogs. */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SystemFileChooser implements FileChooserPort {

    private final Messages messages;

    @Override
    public Optional<Path> chooseToOpen(final @Nullable Window owner, final FileKind kind) {
        Objects.requireNonNull(kind, "kind");
        log.debug("opening the {} open dialog", kind);
        final File picked = chooser(kind).showOpenDialog(owner);
        log.debug("the {} open dialog answered: a file was picked {}", kind, picked != null);
        return Optional.ofNullable(picked).map(File::toPath);
    }

    @Override
    public Optional<Path> chooseToSave(final @Nullable Window owner, final FileKind kind, final Path initial) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(initial, "initial");
        log.debug("opening the {} save dialog at {}", kind, initial);
        final FileChooser chooser = chooser(kind);
        final Path folder = initial.getParent();
        if (folder != null && Files.isDirectory(folder)) {
            chooser.setInitialDirectory(folder.toAbsolutePath().toFile());
        }
        final Path name = initial.getFileName();
        if (name != null) {
            chooser.setInitialFileName(name.toString());
        }
        final File picked = chooser.showSaveDialog(owner);
        log.debug("the {} save dialog answered: a file was picked {}", kind, picked != null);
        return Optional.ofNullable(picked).map(File::toPath).map(SaveNames::undoubled);
    }

    private FileChooser chooser(final FileKind kind) {
        final FileChooser chooser = new FileChooser();
        final MessageKey title = kind.title();
        if (title != null) {
            chooser.setTitle(messages.get(title));
        }
        chooser.getExtensionFilters().addAll(kind.filters());
        return chooser;
    }
}
