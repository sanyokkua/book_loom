package ua.bookloom.ui.state;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import javafx.stage.FileChooser;
import lombok.extern.slf4j.Slf4j;

/** The platform's own open and save dialogs for CSV files, without an owner window: the modal host is not a stage. */
@Slf4j
public final class SystemCsvFileChooser implements CsvFileChooser {

    @Override
    public Optional<Path> chooseToOpen() {
        log.debug("opening the csv open dialog");
        final File picked = chooser().showOpenDialog(null);
        log.debug("the csv open dialog answered: a file was picked {}", picked != null);
        return Optional.ofNullable(picked).map(File::toPath);
    }

    @Override
    public Optional<Path> chooseToSave(final String suggestedName) {
        Objects.requireNonNull(suggestedName, "suggestedName");
        log.debug("opening the csv save dialog starting at {}", suggestedName);
        final FileChooser chooser = chooser();
        chooser.setInitialFileName(suggestedName);
        final File picked = chooser.showSaveDialog(null);
        log.debug("the csv save dialog answered: a file was picked {}", picked != null);
        return Optional.ofNullable(picked).map(File::toPath);
    }

    private static FileChooser chooser() {
        final FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        return chooser;
    }
}
