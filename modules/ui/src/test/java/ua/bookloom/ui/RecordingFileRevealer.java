package ua.bookloom.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import ua.bookloom.ui.state.FileRevealer;

/** A hand-written {@link FileRevealer} that records every file it is asked to reveal instead of opening a folder. */
public final class RecordingFileRevealer implements FileRevealer {

    private final List<Path> revealed = new CopyOnWriteArrayList<>();

    @Override
    public void reveal(final Path file) {
        revealed.add(file);
    }

    public List<Path> revealed() {
        return List.copyOf(revealed);
    }
}
