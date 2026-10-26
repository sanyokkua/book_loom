package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ImportState;
import ua.bookloom.ui.state.ImportVerdict;
import ua.bookloom.ui.state.Tone;

/**
 * Builds the one verdict line of the import page: a banner whose role and glyph follow the verdict's kind, with the
 * controls that kind offers beside its words (a bar and Cancel while reading, "Show them" for a known book, "Choose
 * another file…" for a refusal and "Details" for a damaged file).
 */
@Slf4j
final class ImportVerdictView {

    private static final double CONTROLS_SPACING = 10;
    private static final String ERROR_GLYPH = "⛔";
    private static final String OK_GLYPH = "✓";
    private static final String INFO_GLYPH = "ℹ";

    /** What the verdict's controls do. */
    record Actions(Runnable cancelReading, Runnable showThem, Runnable chooseAnother, Runnable showDetails) {

        /** Rejects a missing action. */
        Actions {
            Objects.requireNonNull(cancelReading, "cancelReading");
            Objects.requireNonNull(showThem, "showThem");
            Objects.requireNonNull(chooseAnother, "chooseAnother");
            Objects.requireNonNull(showDetails, "showDetails");
        }
    }

    private final Messages messages;
    private final Actions actions;

    ImportVerdictView(final Messages messages, final Actions actions) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.actions = Objects.requireNonNull(actions, "actions");
    }

    /**
     * Builds the line.
     *
     * @param verdict the sentence and its kind
     * @param state the state it was worded from, which holds what the controls act on
     * @return the banner, id {@code import-verdict}
     */
    Node build(final ImportVerdict verdict, final ImportState state) {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(state, "state");
        log.debug("building the verdict line of kind {}", verdict.kind());
        final Banner banner =
                new Banner("import-verdict", roleOf(verdict.kind()), glyphOf(verdict.kind()), "", verdict.text());
        banner.getStyleClass().add("import-verdict");
        // A wrapped label's own minimum is one line, so a page shorter than its parts would squash the sentence.
        banner.setMinHeight(Region.USE_PREF_SIZE);
        final HBox controls = new HBox(CONTROLS_SPACING);
        controls.setAlignment(Pos.CENTER_LEFT);
        switch (verdict.kind()) {
            case READING -> reading(controls);
            case ALREADY -> controls.getChildren().add(showThem());
            case DRM, UNSUPPORTED, NO_TEXT -> controls.getChildren().add(chooseAnother());
            case DAMAGED -> controls.getChildren().addAll(chooseAnother(), details());
            case READY, TWO_MARKS, UNRECOGNIZED -> {}
        }
        if (!controls.getChildren().isEmpty()) {
            banner.addDetail(controls);
        }
        return banner;
    }

    private static Tone roleOf(final ImportVerdict.Kind kind) {
        return switch (kind) {
            case READY -> Tone.OK;
            case TWO_MARKS, UNRECOGNIZED -> Tone.WARN;
            case DRM, UNSUPPORTED, DAMAGED, NO_TEXT -> Tone.ERR;
            case READING, ALREADY -> Tone.INFO;
        };
    }

    private static String glyphOf(final ImportVerdict.Kind kind) {
        return switch (kind) {
            case READY -> OK_GLYPH;
            case TWO_MARKS, UNRECOGNIZED -> ImportViews.WARNING_GLYPH;
            case DRM, UNSUPPORTED, DAMAGED, NO_TEXT -> ERROR_GLYPH;
            case READING, ALREADY -> INFO_GLYPH;
        };
    }

    private void reading(final HBox controls) {
        final ProgressBar bar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        bar.setId("import-verdict-bar");
        // The mockup's bar fills the line and Cancel sits at its end.
        bar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(bar, Priority.ALWAYS);
        final Button cancel = button(
                "import-verdict-cancel",
                MessageKey.IMPORT_VERDICT_CANCEL,
                MessageKey.IMPORT_VERDICT_CANCEL_TIP,
                "btn-ghost",
                actions.cancelReading());
        controls.getChildren().addAll(bar, cancel);
    }

    private Button showThem() {
        return button(
                "import-verdict-show",
                MessageKey.IMPORT_VERDICT_ALREADY_SHOW,
                MessageKey.IMPORT_VERDICT_ALREADY_SHOW_TIP,
                "btn-link",
                actions.showThem());
    }

    private Button chooseAnother() {
        return button(
                "import-verdict-choose",
                MessageKey.IMPORT_CHOOSE_ANOTHER,
                MessageKey.IMPORT_CHOOSE_ANOTHER_TIP,
                "btn-secondary",
                actions.chooseAnother());
    }

    private Button details() {
        return button(
                "import-verdict-details",
                MessageKey.IMPORT_VERDICT_DAMAGED_DETAILS,
                MessageKey.IMPORT_VERDICT_DAMAGED_DETAILS_TIP,
                "btn-link",
                actions.showDetails());
    }

    private Button button(
            final String id,
            final MessageKey label,
            final MessageKey tip,
            final String styleClass,
            final Runnable action) {
        final Button button = new Button(messages.get(label));
        button.setId(id);
        button.getStyleClass().add(styleClass);
        Tips.install(messages, button, tip);
        button.setOnAction(event -> action.run());
        return button;
    }
}
