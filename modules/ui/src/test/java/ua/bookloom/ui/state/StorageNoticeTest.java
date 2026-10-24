package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedStorageInfo;
import ua.bookloom.ui.i18n.MessageKey;

/** A launch that moved the stored work aside says so once, naming the file; any other launch says nothing. */
class StorageNoticeTest {

    // IF the move were silent, THEN the person would find every project gone and no file to look for.
    @Test
    void announceSetAside_workMovedAside_warnsNamingTheFile() {
        final ScriptedStorageInfo storage = ScriptedStorageInfo.typical();
        storage.setAsideAs(Path.of("/data/BookLoom/bookloom.db.old-20261020-101530"));
        final RecordingToasts toasts = new RecordingToasts();

        new StorageNotice(storage, toasts).announceSetAside();

        assertThat(toasts.raised())
                .containsExactly(new RecordingToasts.Raised(
                        "warning", MessageKey.TOAST_STORAGE_SET_ASIDE, List.of("bookloom.db.old-20261020-101530")));
    }

    @Test
    void announceSetAside_nothingMoved_raisesNothing() {
        final RecordingToasts toasts = new RecordingToasts();

        new StorageNotice(ScriptedStorageInfo.typical(), toasts).announceSetAside();

        assertThat(toasts.raised()).isEmpty();
    }
}
