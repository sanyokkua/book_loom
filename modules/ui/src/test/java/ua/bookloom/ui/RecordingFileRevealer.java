package ua.bookloom.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import ua.bookloom.ui.state.FileRevealer;

/** A hand-written {@link FileRevealer} that records every file it is asked to reveal or open instead of opening a folder. */
public final class RecordingFileRevealer implements FileRevealer {

    private final List<Path> revealed = new CopyOnWriteArrayList<>();
    private final List<Path> opened = new CopyOnWriteArrayList<>();

    @Override
    public void reveal(final Path file) {
        revealed.add(file);
    }

    @Override
    public void open(final Path file) {
        opened.add(file);
    }

    public List<Path> opened() {
        return List.copyOf(opened);
    }

    public List<Path> revealed() {
        return List.copyOf(revealed);
    }
}
