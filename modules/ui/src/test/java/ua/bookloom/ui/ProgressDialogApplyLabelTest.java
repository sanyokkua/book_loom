package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.state.ActivityKind;

/** The dialog's main action is named for what it applies: the brief, a file name, and nothing for the export. */
class ProgressDialogApplyLabelTest extends ProgressWorkTestBase {

    // IF every suggestion said "Apply to the brief", THEN a file name would read as a change to the brief.
    @ParameterizedTest
    @CsvSource({
        "SUGGEST_STYLE, Apply to the brief, Nothing is written to the brief until you apply it.",
        "SUGGEST_NAME, Use this name, Cancel keeps the current name."
    })
    void apply_byKind_isNamedForWhatItApplies(final ActivityKind kind, final String label, final String footnote)
            throws TimeoutException {
        begin(kind);
        await(this::isDialogShown);

        assertThat(((Button) required("progress-apply")).getText()).isEqualTo(label);
        assertThat(((Label) required("progress-footnote")).getText()).isEqualTo(footnote);
    }

    // IF the export offered an Apply, THEN a person would look for a result to apply where the file is written.
    @Test
    void export_dialog_offersNoApplyAndNoWriteNow() throws TimeoutException {
        begin(ActivityKind.EXPORT);
        await(this::isDialogShown);

        assertThat(optional("progress-apply")).isNull();
        assertThat(optional("progress-write-now")).isNull();
    }

    private javafx.scene.Node optional(final String id) {
        return scene.getRoot().lookup("#" + id);
    }
}
