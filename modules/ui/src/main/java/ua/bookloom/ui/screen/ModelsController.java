package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelEntry;
import ua.bookloom.ui.state.ModelForm;
import ua.bookloom.ui.state.ModelRoster;
import ua.bookloom.ui.state.TechnicalDetails;

/**
 * The Models area of the settings screen: the chooser over every provider's models, the form of the chosen model and its
 * pinned footer. Choosing another model while the form holds changes asks first, naming the model.
 *
 * <p>The roster and the form outlive this controller, so they are observed through weak listeners held by the fields
 * below, which live exactly as long as the nodes that use them.
 */
@Slf4j
public final class ModelsController {

    private final ModelRoster roster;
    private final ModelForm form;
    private final Messages messages;
    private final TechnicalDetails technical;
    private final SettingsTabGuard guard;
    private final ChangeListener<ModelEntry> onSelected = (observed, was, now) -> showSelected(now);
    private final ChangeListener<ModelEntry> onChosen = (observed, was, now) -> chosen(now);
    // A new offer replaces the chooser's items, which clears its pick; the pick is put back.
    private final ListChangeListener<ModelEntry> onOffer = change -> showCurrent();

    // Set while this controller moves the chooser itself, so the move is not taken for a person's choice.
    private boolean syncing;
    private ModelsFormView formView;

    @FXML
    private ComboBox<ModelEntry> model;

    @FXML
    private Button refresh;

    @FXML
    private Label listing;

    @FXML
    private Label explain;

    @FXML
    private Label empty;

    @FXML
    private ScrollPane scroll;

    @FXML
    private VBox body;

    @FXML
    private HBox foot;

    @FXML
    private Label dirty;

    @FXML
    private Label saved;

    @FXML
    private Button cancel;

    @FXML
    private Button save;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param roster the models on offer
     * @param form what the chosen model's form holds and saves
     * @param messages the catalogue the built nodes are worded from
     * @param technical whether the technical names are shown
     * @param guard asks before a change of model would drop changes
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public ModelsController(
            final ModelRoster roster,
            final ModelForm form,
            final Messages messages,
            final TechnicalDetails technical,
            final SettingsTabGuard guard) {
        this.roster = Objects.requireNonNull(roster, "roster");
        this.form = Objects.requireNonNull(form, "form");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.technical = Objects.requireNonNull(technical, "technical");
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @FXML
    void initialize() {
        log.debug("building the models area");
        formView = new ModelsFormView(form, messages, technical);
        body.getChildren().setAll(formView.node());
        buildChooser();
        bindVisibility();
        bindFooter();
        refresh.setOnAction(event -> roster.refresh());
        cancel.setOnAction(event -> form.discard());
        save.setOnAction(event -> form.save(() -> log.debug("model form saved")));
        Tips.install(messages, refresh, MessageKey.SETTINGS_MODELS_REFRESH_TIP);
        Tips.install(messages, cancel, MessageKey.SETTINGS_FORM_CANCEL_TIP);
        Tips.install(messages, save, MessageKey.SETTINGS_MODELS_SAVE_TIP);
        form.selected().addListener(new WeakChangeListener<>(onSelected));
        showSelected(form.selected().get());
    }

    private void buildChooser() {
        model.setItems(roster.entries());
        model.setConverter(new StringConverter<>() {
            @Override
            public String toString(final @Nullable ModelEntry entry) {
                return entry == null
                        ? ""
                        : messages.get(MessageKey.SETTINGS_MODELS_ENTRY, entry.modelId(), entry.providerName());
            }

            @Override
            public ModelEntry fromString(final String text) {
                throw new UnsupportedOperationException("the model is picked, never typed");
            }
        });
        Tips.install(messages, model, MessageKey.SETTINGS_MODELS_MODEL_TIP);
        model.valueProperty().addListener(new WeakChangeListener<>(onChosen));
        roster.entries().addListener(new WeakListChangeListener<>(onOffer));
    }

    private void bindVisibility() {
        final BooleanBinding none = Bindings.isEmpty(roster.entries());
        Visibility.bind(listing, roster.listing());
        Visibility.bind(empty, Bindings.and(none, roster.listing().not()));
        Visibility.bind(scroll, none.not());
        Visibility.bind(foot, none.not());
        Visibility.bind(explain, none.not());
        model.disableProperty().bind(none);
        refresh.disableProperty().bind(roster.listing());
    }

    private void bindFooter() {
        Visibility.bind(dirty, form.dirty());
        Visibility.bind(saved, form.justSaved());
        cancel.disableProperty().bind(form.dirty().not());
        save.disableProperty().bind(form.dirty().not().or(form.saving()));
    }

    private void showCurrent() {
        showSelected(form.selected().get());
    }

    private void showSelected(final @Nullable ModelEntry entry) {
        if (!Objects.equals(model.getValue(), entry)) {
            moveChooser(entry);
        }
    }

    private void moveChooser(final @Nullable ModelEntry entry) {
        syncing = true;
        try {
            model.setValue(entry);
        } finally {
            syncing = false;
        }
    }

    private void chosen(final @Nullable ModelEntry entry) {
        if (syncing || entry == null || entry.equals(form.selected().get())) {
            return;
        }
        final ModelEntry shown = form.selected().get();
        if (!form.dirty().get()) {
            form.select(entry);
            return;
        }
        log.debug("model change vetoed: the form of {} holds unsaved changes", form.subject());
        moveChooser(shown);
        guard.guardModel(() -> form.select(entry));
    }
}
