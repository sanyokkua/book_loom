package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.collections.transformation.FilteredList;
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
import ua.bookloom.ui.control.ModelFilterField;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.AdviceReader;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ModelAdviceSources;
import ua.bookloom.ui.state.ModelEntry;
import ua.bookloom.ui.state.ModelForm;
import ua.bookloom.ui.state.ModelNameFilter;
import ua.bookloom.ui.state.ModelQuantChips;
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
    private final ModelQuantChips quantChips;
    private final AdviceReader adviceReader;
    private final CurrentProject current;
    private final LanguageNames names;
    private final ChangeListener<ModelEntry> onSelected = (observed, was, now) -> showSelected(now);
    private final ChangeListener<ModelEntry> onChosen = (observed, was, now) -> chosen(now);
    // A new offer replaces the chooser's items, which clears its pick; the pick is put back.
    private final ListChangeListener<ModelEntry> onOffer = change -> offerChanged();

    private @Nullable FilteredList<ModelEntry> offered;
    private @Nullable ModelFilterField filter;

    // Set while this controller moves the chooser itself, so the move is not taken for a person's choice.
    private boolean syncing;

    private static final int MAX_VISIBLE_ROWS = 10;

    @FXML
    private ComboBox<ModelEntry> model;

    @FXML
    private Button refresh;

    @FXML
    private Label listing;

    @FXML
    private Label explain;

    @FXML
    private Label unanswered;

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
     * @param quantChips the quant chips drawn beside the models
     * @param advice where the readings behind the advice lines come from, the open project they are about, and how
     *     languages are named
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public ModelsController(
            final ModelRoster roster,
            final ModelForm form,
            final Messages messages,
            final TechnicalDetails technical,
            final SettingsTabGuard guard,
            final ModelQuantChips quantChips,
            final ModelAdviceSources advice) {
        this.roster = Objects.requireNonNull(roster, "roster");
        this.form = Objects.requireNonNull(form, "form");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.technical = Objects.requireNonNull(technical, "technical");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.quantChips = Objects.requireNonNull(quantChips, "quantChips");
        this.adviceReader = Objects.requireNonNull(advice, "advice").reader();
        this.current = advice.current();
        this.names = advice.names();
    }

    @FXML
    void initialize() {
        log.debug("building the models area");
        final ModelAdviceSection advice = new ModelAdviceSection(adviceReader, current, form, messages, names);
        body.getChildren().setAll(advice.node(), new ModelsFormView(form, messages, technical).node(), advice.foot());
        buildChooser();
        addFilter();
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
        final FilteredList<ModelEntry> shown = new FilteredList<>(roster.entries());
        offered = shown;
        model.setEditable(true);
        model.setItems(shown);
        model.setCellFactory(list -> new ModelEntryCell(quantChips, this::label, messages));
        offerChanged();
        model.setConverter(new StringConverter<>() {
            @Override
            public String toString(final @Nullable ModelEntry entry) {
                return label(entry);
            }

            @Override
            public @Nullable ModelEntry fromString(final String text) {
                return roster.entries().stream()
                        .filter(entry -> label(entry).equals(text))
                        .findFirst()
                        .orElse(null);
            }
        });
        Tips.install(messages, model, MessageKey.SETTINGS_MODELS_MODEL_TIP);
        model.valueProperty().addListener(new WeakChangeListener<>(onChosen));
        model.getEditor().textProperty().addListener((observed, was, now) -> narrow(now));
        roster.entries().addListener(new WeakListChangeListener<>(onOffer));
    }

    private String label(final @Nullable ModelEntry entry) {
        return entry == null
                ? ""
                : messages.get(MessageKey.SETTINGS_MODELS_ENTRY, entry.modelId(), entry.providerName());
    }

    // Narrows the offered models to the names containing the typed text; a pick shows its own name, which filters
    // nothing.
    private void narrow(final String typed) {
        final FilteredList<ModelEntry> shown = offered;
        if (syncing || shown == null) {
            return;
        }
        final boolean all = typed.strip().isEmpty() || typed.equals(label(model.getValue()));
        log.debug(
                "narrowing the model chooser, filter of {} character(s)",
                typed.strip().length());
        syncing = true;
        try {
            if (model.isShowing()) {
                model.hide();
            }
            shown.setPredicate(predicate(all ? "" : typed, filterText()));
            model.setVisibleRowCount(Math.clamp(shown.size(), 1, MAX_VISIBLE_ROWS));
            model.getEditor().setText(typed);
            model.getEditor().positionCaret(typed.length());
        } finally {
            syncing = false;
        }
        showNoneHint();
        if (!all && model.getEditor().isFocused() && !shown.isEmpty()) {
            model.show();
        }
    }

    // No predicate at all while nothing narrows: a FilteredList with a live predicate misreports a setAll of its
    // source.
    private @Nullable Predicate<ModelEntry> predicate(final String typed, final String filterTyped) {
        return typed.isBlank() && filterTyped.isBlank()
                ? null
                : entry -> matches(entry, typed) && matchesId(entry, filterTyped);
    }

    // The filter field names models, not providers: "llama" must not match every model of Ollama.
    private static boolean matchesId(final ModelEntry entry, final String typed) {
        return ModelNameFilter.matches(entry.modelId(), entry.modelId(), typed);
    }

    private boolean matches(final ModelEntry entry, final String typed) {
        return ModelNameFilter.matches(entry.modelId(), label(entry), typed);
    }

    private String filterText() {
        final ModelFilterField field = filter;
        return field == null ? "" : field.text();
    }

    private void showNoneHint() {
        final ModelFilterField field = filter;
        final FilteredList<ModelEntry> shown = offered;
        if (field != null && shown != null) {
            field.showNone(shown.isEmpty() && !roster.entries().isEmpty());
        }
    }

    // The filter field only hides rows: the chosen model, the form and the editor text are left as they are, and the
    // combo's value is put back should the skin clear it when its row goes.
    private void addFilter() {
        final ModelFilterField field =
                new ModelFilterField(messages, "models-filter", "models-filter-none", this::filterTyped, () -> {
                    model.getEditor().requestFocus();
                    model.show();
                });
        filter = field;
        field.field().disableProperty().bind(Bindings.isEmpty(roster.entries()));
        final VBox card = (VBox) model.getParent().getParent();
        card.getChildren().add(card.getChildren().indexOf(model.getParent()) + 1, field.node());
    }

    private boolean filterTyped(final String typed) {
        final FilteredList<ModelEntry> shown = offered;
        if (syncing || shown == null) {
            return true;
        }
        final String editorText = model.getEditor().getText();
        final ModelEntry chosen = model.getValue();
        syncing = true;
        try {
            if (model.isShowing()) {
                model.hide();
            }
            final boolean all = editorText.strip().isEmpty() || editorText.equals(label(chosen));
            shown.setPredicate(predicate(all ? "" : editorText, typed));
            model.setVisibleRowCount(Math.clamp(shown.size(), 1, MAX_VISIBLE_ROWS));
            if (!Objects.equals(model.getValue(), chosen)) {
                model.setValue(chosen);
            }
            model.getEditor().setText(editorText);
        } finally {
            syncing = false;
        }
        return !shown.isEmpty() || roster.entries().isEmpty();
    }

    private void bindVisibility() {
        final BooleanBinding none = Bindings.isEmpty(roster.entries());
        Visibility.bind(listing, roster.listing());
        Visibility.bind(
                unanswered,
                Bindings.createBooleanBinding(
                        () -> !roster.listing().get()
                                && !roster.unanswered().get().isEmpty(),
                        roster.listing(),
                        roster.unanswered()));
        unanswered
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> messages.get(
                                MessageKey.SETTINGS_MODELS_UNANSWERED,
                                String.join(", ", roster.unanswered().get()),
                                roster.unanswered().get().size()),
                        roster.unanswered()));
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

    private void offerChanged() {
        roster.entries().stream()
                .collect(Collectors.groupingBy(ModelEntry::providerId))
                .forEach((provider, entries) -> quantChips.fromNames(
                        provider, entries.stream().map(ModelEntry::modelId).toList()));
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
            if (offered != null) {
                offered.setPredicate(predicate("", filterText()));
            }
            model.setValue(entry);
            model.getEditor().setText(label(entry));
        } finally {
            syncing = false;
        }
    }

    private void chosen(final @Nullable ModelEntry entry) {
        if (syncing) {
            return;
        }
        if (entry == null) {
            moveChooser(form.selected().get());
            return;
        }
        if (entry.equals(form.selected().get())) {
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
