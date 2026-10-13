package ua.bookloom.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.state.CsvFileChooser;

/** A hand-written {@link CsvFileChooser} that records what it was asked and answers with a scripted file. */
public final class RecordingCsvChooser implements CsvFileChooser {

    private final List<String> asked = new CopyOnWriteArrayList<>();
    private volatile @Nullable Path answer;

    /** Makes every later choice answer with {@code chosen}; {@code null} answers that the person cancelled. */
    public void answer(final @Nullable Path chosen) {
        answer = chosen;
    }

    /** Each choice asked for, in order: {@code open}, or {@code save:<suggested name>}. */
    public List<String> asked() {
        return List.copyOf(asked);
    }

    @Override
    public Optional<Path> chooseToOpen() {
        asked.add("open");
        return Optional.ofNullable(answer);
    }

    @Override
    public Optional<Path> chooseToSave(final String suggestedName) {
        asked.add("save:" + Objects.requireNonNull(suggestedName, "suggestedName"));
        return Optional.ofNullable(answer);
    }
}
