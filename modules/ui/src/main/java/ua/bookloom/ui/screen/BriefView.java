package ua.bookloom.ui.screen;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.ToggleSwitch;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;

/**
 * The brief as it is drawn while a book is open: the live Languages and destination cards, the cards nothing reads
 * yet, and the two buttons that move around the workflow.
 *
 * <p>Split from the controller because the controller's other job is swapping this for the no-book state, and because
 * this holds the two-way wiring between the controls and the view model. That wiring is what {@code applying} guards:
 * a proposal the view model writes into the destination field must not come back as if the person had typed it, or the
 * next target change would be ignored. The view model outlives this view, so it is observed through weak listeners
 * held by the fields below; the controls' own listeners capture this view, which is what keeps it reachable for as
 * long as its nodes are on show.
 */
@Slf4j
final class BriefView {

    private static final double COLUMN_SPACING = 14;
    private static final double COLUMN_WIDTH = 300;
    private static final double CARD_SPACING = 14;
    private static final double ACTION_SPACING = 10;

    private final BookBriefViewModel viewModel;
    private final Messages messages;
    private final Navigator navigator;
    private final ComboBox<String> target = new ComboBox<>();
    private final TextField destination = new TextField();
    private final ToggleSwitch overwrite = new ToggleSwitch();
    private final Button continueButton = new Button();
    private final Node root;
    private final ChangeListener<String> onTarget = (observed, was, now) -> showTarget(now);
    private final ChangeListener<String> onDestination = (observed, was, now) -> showDestination(now);
    private final ChangeListener<Boolean> onOverwrite = (observed, was, now) -> showOverwrite(now);
    // True while a value the view model published is being written into a control, so the control's own listener
    // does not hand it straight back as a person's choice. FX thread only.
    private boolean applying;

    BriefView(final BookBriefViewModel viewModel, final Messages messages, final Navigator navigator) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        log.debug("building the brief for {}", viewModel.openedBook().get().source());
        this.root = build();
        wire();
        // The warning about an occupied destination is only as fresh as the last look, and the view model outlives
        // every visit, so each visit asks again.
        viewModel.refresh();
    }

    Node root() {
        return root;
    }

    private Node build() {
        final Label subtitle = ImportViews.wrapped(messages.get(MessageKey.BRIEF_SUBTITLE), "muted");
        subtitle.setId("brief-subtitle");
        final VBox left = column(languagesCard(), destinationCard(), BriefCards.tone(messages));
        final VBox right =
                column(BriefCards.policies(messages), BriefCards.alsoTranslate(messages), BriefCards.quality(messages));
        final HBox columns = new HBox(COLUMN_SPACING, left, right);
        return new VBox(CARD_SPACING, subtitle, columns, actions());
    }

    private static VBox column(final Node... cards) {
        final VBox column = new VBox(CARD_SPACING, cards);
        column.setPrefWidth(COLUMN_WIDTH);
        column.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(column, Priority.ALWAYS);
        return column;
    }

    private Node languagesCard() {
        final ComboBox<String> source = sourcePicker();
        target.setId("brief-target");
        target.getItems().setAll(BookBriefViewModel.TARGET_LANGUAGES);
        target.setConverter(new StringConverter<>() {
            @Override
            public String toString(final String code) {
                return code == null ? "" : ImportViews.languageLabel(messages, code);
            }

            @Override
            public String fromString(final String label) {
                throw new UnsupportedOperationException("the target picker is not editable");
            }
        });
        return BriefCards.card(
                "brief-languages-card",
                messages,
                MessageKey.BRIEF_CARD_LANGUAGES,
                false,
                BriefCards.field(messages, MessageKey.BRIEF_SOURCE_LABEL, source),
                BriefCards.field(messages, MessageKey.BRIEF_TARGET_LABEL, target));
    }

    // The reference draws a picker here. It is disabled and holds the one thing the book declared (or the words for
    // "not declared"), because the source is a fact to show and not a choice to make.
    private ComboBox<String> sourcePicker() {
        final Optional<String> declared = viewModel.sourceLanguage();
        log.debug("source language declared by the book: {}", declared);
        final String shown = declared.map(code -> ImportViews.languageLabel(messages, code))
                .orElseGet(() -> messages.get(MessageKey.BRIEF_SOURCE_UNDECLARED));
        final ComboBox<String> source = new ComboBox<>();
        source.setId("brief-source");
        source.getItems().setAll(shown);
        source.setValue(shown);
        source.setDisable(true);
        return source;
    }

    private Node destinationCard() {
        destination.setId("brief-destination");
        HBox.setHgrow(destination, Priority.ALWAYS);
        final Button browse = new Button(messages.get(MessageKey.BRIEF_DESTINATION_BROWSE));
        browse.setId("brief-destination-browse");
        browse.getStyleClass().add("btn-secondary");
        browse.setOnAction(event -> chooseDestination());
        final HBox path = new HBox(ACTION_SPACING, destination, browse);
        path.setAlignment(Pos.CENTER_LEFT);
        overwrite.setId("brief-overwrite");
        overwrite.setText(messages.get(MessageKey.BRIEF_OVERWRITE_LABEL));
        return BriefCards.card(
                "brief-destination-card",
                messages,
                MessageKey.BRIEF_CARD_DESTINATION,
                false,
                BriefCards.field(messages, MessageKey.BRIEF_DESTINATION_LABEL, path),
                existsBanner(),
                overwrite);
    }

    private Node existsBanner() {
        final Node banner = ImportViews.banner(
                "brief-destination-exists",
                "banner-warn",
                ImportViews.WARNING_GLYPH,
                List.of(
                        ImportViews.wrapped(messages.get(MessageKey.BRIEF_DESTINATION_EXISTS_TITLE), "banner-title"),
                        ImportViews.wrapped(messages.get(MessageKey.BRIEF_DESTINATION_EXISTS_TEXT), "banner-text")));
        banner.visibleProperty().bind(viewModel.destinationExists());
        banner.managedProperty().bind(banner.visibleProperty());
        return banner;
    }

    private Node actions() {
        final Button back = new Button(messages.get(MessageKey.BRIEF_BACK));
        back.setId("brief-back");
        back.getStyleClass().add("btn-ghost");
        back.setOnAction(event -> navigator.navigate(ViewNames.IMPORT));
        continueButton.setText(messages.get(MessageKey.BRIEF_CONTINUE));
        continueButton.setId("brief-continue");
        continueButton.getStyleClass().add("btn-primary");
        continueButton.setOnAction(event -> onContinue());
        final HBox row = new HBox(ACTION_SPACING, back, continueButton);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void wire() {
        showTarget(viewModel.targetLanguage().get());
        showDestination(viewModel.destination().get());
        showOverwrite(viewModel.overwrite().get());
        target.valueProperty().addListener((observed, was, now) -> onTargetPicked(now));
        destination.textProperty().addListener((observed, was, now) -> onDestinationTyped(now));
        overwrite.selectedProperty().addListener((observed, was, now) -> onOverwriteSwitched(now));
        viewModel.targetLanguage().addListener(new WeakChangeListener<>(onTarget));
        viewModel.destination().addListener(new WeakChangeListener<>(onDestination));
        viewModel.overwrite().addListener(new WeakChangeListener<>(onOverwrite));
    }

    private void onTargetPicked(final String code) {
        if (applying) {
            return;
        }
        log.debug("target {} picked", code);
        viewModel.selectTarget(code);
    }

    private void onDestinationTyped(final String text) {
        if (applying) {
            return;
        }
        viewModel.editDestination(text);
    }

    private void onOverwriteSwitched(final boolean allowed) {
        if (applying) {
            return;
        }
        log.debug("overwrite switched to {}", allowed);
        viewModel.setOverwrite(allowed);
    }

    private void showTarget(final String code) {
        applying = true;
        try {
            target.setValue(code);
        } finally {
            applying = false;
        }
    }

    private void showDestination(final String text) {
        if (text.equals(destination.getText())) {
            log.trace("destination field already shows '{}'", text);
        } else {
            applying = true;
            try {
                destination.setText(text);
            } finally {
                applying = false;
            }
        }
        final boolean usable = viewModel.request().isPresent();
        log.debug("destination shown, continue available: {}", usable);
        continueButton.setDisable(!usable);
    }

    private void showOverwrite(final boolean allowed) {
        applying = true;
        try {
            overwrite.setSelected(allowed);
        } finally {
            applying = false;
        }
    }

    private void onContinue() {
        final Optional<ViewNames> next = navigator.nextAvailableStep(ViewNames.BOOK_BRIEF);
        log.debug("continue pressed, the next step is {}", next);
        next.ifPresent(navigator::navigate);
    }

    private void chooseDestination() {
        final FileChooser chooser = new FileChooser();
        chooser.setTitle(messages.get(MessageKey.BRIEF_DESTINATION_CHOOSER));
        viewModel.destinationPath().ifPresent(current -> startAt(chooser, current));
        // java.io.File appears only as the chooser's answer; it becomes a Path at once (null when cancelled).
        final File picked = chooser.showSaveDialog(destination.getScene().getWindow());
        log.debug("destination chooser answered with a file: {}", picked != null);
        if (picked != null) {
            viewModel.chooseDestination(picked.toPath());
        }
    }

    private static void startAt(final FileChooser chooser, final Path current) {
        final Path name = current.getFileName();
        final Path folder = current.toAbsolutePath().getParent();
        if (name != null) {
            chooser.setInitialFileName(name.toString());
        }
        if (folder != null) {
            // Not checked with the file system here: the toolkit ignores an initial folder that is not one.
            chooser.setInitialDirectory(folder.toFile());
        }
    }
}
