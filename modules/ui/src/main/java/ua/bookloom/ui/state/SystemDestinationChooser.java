package ua.bookloom.ui.state;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import javafx.stage.FileChooser;
import lombok.extern.slf4j.Slf4j;

/** The platform's own save dialog, without an owner window: the modal host is not a stage. */
@Slf4j
public final class SystemDestinationChooser implements DestinationChooser {

    @Override
    public Optional<Path> choose(final Path initial) {
        Objects.requireNonNull(initial, "initial");
        log.debug("opening the save dialog at {}", initial);
        final FileChooser chooser = new FileChooser();
        final Path folder = initial.toAbsolutePath().getParent();
        if (folder != null && Files.isDirectory(folder)) {
            chooser.setInitialDirectory(folder.toFile());
        }
        final Path name = initial.getFileName();
        if (name != null) {
            chooser.setInitialFileName(name.toString());
        }
        final File picked = chooser.showSaveDialog(null);
        log.debug("the save dialog answered: a file was picked {}", picked != null);
        return Optional.ofNullable(picked).map(File::toPath);
    }
}
