package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.dialog.ConfirmPrompt;
import ua.bookloom.ui.dialog.ProviderTestDialog;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.ProviderDraft;
import ua.bookloom.ui.state.ProviderForm;
import ua.bookloom.ui.state.ProviderInUse;
import ua.bookloom.ui.state.ProviderRow;
import ua.bookloom.ui.state.ProviderSwitch;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * The settings screen: the providers list, the open provider's form and its footer, and the tab strip.
 *
 * <p>The rows are built here from the view model's list because their number depends on data; the form is drawn by
 * {@link ProviderFormView}. Every way of leaving a form that holds unsaved typing (another row, New provider, another
 * tab) goes through {@link SettingsTabGuard}, which asks first. The view model outlives this controller, so it is observed
 * through weak listeners held by the fields below, which live exactly as long as the nodes that use them.
 */
@Slf4j
public final class SettingsController {

    private final SettingsViewModel viewModel;
    private final ProviderForm form;
    private final Messages messages;
    private final ConfirmPrompt confirm;
    private final ProviderTestDialog testDialog;
    private final ProviderSwitch providerSwitch;
    private final ActivityTracker activities;
    private final SettingsTabGuard guard;
    private final SettingsTabEntries tabEntries;
    private final ProviderInUse inUse;
    private ProviderRowList rowList;
    private final ListChangeListener<ProviderRow> onProviders = change -> onProvidersLoaded();
    private final ChangeListener<String> onSelection = (observed, was, now) -> rowList.mark();
    private final ChangeListener<ProviderForm.Spot> onFocusRequest = (observed, was, now) -> onFocusRequested(now);
    private final ChangeListener<Tab> onTab = (observed, was, now) -> onTabChanged(was, now);

    // Set while this controller moves the tab selection itself, so the change is not taken for a person's click.
    private boolean movingTab;
    private ProviderFormView formView;

    @FXML
    private TabPane tabs;

    @FXML
    private VBox providerList;

    @FXML
    private Label providerEmpty;

    @FXML
    private Button providerAdd;

    @FXML
    private VBox formCard;

    @FXML
    private Label formTitle;

    @FXML
    private Label formBadge;

    @FXML
    private Button providerDelete;

    @FXML
    private Button formUse;

    @FXML
    private Label formUseReason;

    @FXML
    private Label deleteReason;

    @FXML
    private ScrollPane formScroll;

    @FXML
    private VBox formBody;

    @FXML
    private Label formDirty;

    @FXML
    private Label formSaved;

    @FXML
    private Button formCancel;

    @FXML
    private Button formSave;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param viewModel the state this screen shows
     * @param form what the open provider's form holds, saves and deletes
     * @param messages the catalogue the built rows are worded from
     * @param confirm the question asked before a provider is deleted
     * @param testDialog the card that shows what a test sent and got back
     * @param providerSwitch what Use this one does
     * @param activities the model work under way, which keeps the provider in use from being deleted
     * @param guard asks before a move would drop typing
     * @param tabEntries what entering a tab reads again
     * @param inUse whether the open provider is the one the translation run uses
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public SettingsController(
            final SettingsViewModel viewModel,
            final ProviderForm form,
            final Messages messages,
            final ConfirmPrompt confirm,
            final ProviderTestDialog testDialog,
            final ProviderSwitch providerSwitch,
            final ActivityTracker activities,
            final SettingsTabGuard guard,
            final SettingsTabEntries tabEntries,
            final ProviderInUse inUse) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.form = Objects.requireNonNull(form, "form");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.confirm = Objects.requireNonNull(confirm, "confirm");
        this.testDialog = Objects.requireNonNull(testDialog, "testDialog");
        this.providerSwitch = Objects.requireNonNull(providerSwitch, "providerSwitch");
        this.activities = Objects.requireNonNull(activities, "activities");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.tabEntries = Objects.requireNonNull(tabEntries, "tabEntries");
        this.inUse = Objects.requireNonNull(inUse, "inUse");
    }

    @FXML
    void initialize() {
        log.debug("building the settings screen");
        formView = new ProviderFormView(viewModel, form, messages, testDialog);
        rowList = new ProviderRowList(viewModel, messages, providerList, this::open);
        formBody.getChildren().setAll(formView.node());
        installTips();
        providerAdd.setOnAction(event -> guard.guardProvider(viewModel::startAdding));
        providerDelete.setOnAction(event -> askDelete());
        formUse.setOnAction(event -> providerSwitch.use());
        formCancel.setOnAction(event -> form.discard());
        formSave.setOnAction(event -> form.save(() -> log.debug("provider form saved")));
        showProviders();
        viewModel.providers().addListener(new WeakListChangeListener<>(onProviders));
        viewModel.selectedProviderId().addListener(new WeakChangeListener<>(onSelection));
        viewModel.editedProviderId().addListener(new WeakChangeListener<>(onSelection));
        bindHeader();
        bindFooter();
        tabs.getSelectionModel().selectedItemProperty().addListener(new WeakChangeListener<>(onTab));
        if (!viewModel.providers().isEmpty()) {
            viewModel.refreshModels();
        }
        form.focusRequest().addListener(new WeakChangeListener<>(onFocusRequest));
    }

    private void installTips() {
        Tips.install(messages, providerAdd, MessageKey.SETTINGS_PROVIDER_ADD_TIP);
        Tips.install(messages, providerDelete, MessageKey.SETTINGS_PROVIDER_DELETE_TIP);
        Tips.install(messages, formUse, MessageKey.SETTINGS_PROVIDER_USE_TIP);
        Tips.install(messages, formCancel, MessageKey.SETTINGS_FORM_CANCEL_TIP);
        Tips.installLive(formSave, Bindings.createStringBinding(this::saveTip, form.findings()));
    }

    // Save stays pressable so a press can list every problem; until then its explanation names the first one.
    private String saveTip() {
        return form.findings().get().stream()
                .filter(ProviderDraft.FormFinding::blocking)
                .findFirst()
                .map(finding -> messages.get(
                        MessageKey.SETTINGS_FORM_SAVE_BLOCKED,
                        messages.get(finding.key(), finding.args().toArray())))
                .orElseGet(() -> messages.get(MessageKey.SETTINGS_FORM_SAVE_TIP));
    }

    private void bindHeader() {
        final Observable[] observed = {
            viewModel.editedProviderId(), viewModel.selectedProviderId(), viewModel.adding(), viewModel.providers()
        };
        formTitle
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> viewModel.adding().get()
                                ? messages.get(MessageKey.SETTINGS_FORM_NEW_TITLE)
                                : viewModel.providerName(
                                        viewModel.editedProviderId().get()),
                        observed));
        formBadge
                .visibleProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> !viewModel.adding().get()
                                && !viewModel.editedProviderId().get().isEmpty()
                                && viewModel
                                        .editedProviderId()
                                        .get()
                                        .equals(viewModel.selectedProviderId().get()),
                        observed));
        formBadge.managedProperty().bind(formBadge.visibleProperty());
        bindDelete();
        bindUse(observed);
        bindEmptyState();
    }

    // Offered for a saved provider that is not in use; it is off while the form holds more than a typed model.
    private void bindUse(final Observable[] observed) {
        final BooleanBinding offered = Bindings.createBooleanBinding(
                () -> !viewModel.adding().get()
                        && !viewModel.editedProviderId().get().isEmpty()
                        && !viewModel
                                .editedProviderId()
                                .get()
                                .equals(viewModel.selectedProviderId().get()),
                observed);
        formUse.visibleProperty().bind(offered);
        formUse.managedProperty().bind(offered);
        formUse.disableProperty().bind(form.canUseThis().not().or(form.saving()));
        final BooleanBinding blockedByChanges = offered.and(form.canUseThis().not());
        formUseReason.visibleProperty().bind(blockedByChanges);
        formUseReason.managedProperty().bind(blockedByChanges);
    }

    private void bindDelete() {
        final BooleanBinding busy = busyOnCurrent();
        providerDelete
                .disableProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> viewModel.adding().get()
                                || viewModel.editedProviderId().get().isEmpty()
                                || form.saving().get()
                                || busy.get(),
                        viewModel.adding(),
                        viewModel.editedProviderId(),
                        form.saving(),
                        busy));
        // A disabled button shows no hover text, so the reason is a line beside it.
        deleteReason.visibleProperty().bind(busy);
        deleteReason.managedProperty().bind(busy);
    }

    // Model work is under way and the open provider is the one it uses, or a run, even a paused one, uses it.
    private BooleanBinding busyOnCurrent() {
        final ObjectBinding<ActivityKind> blocker = activities.blocker(ActivityKind.PROVIDER_INFERENCE_TEST);
        final BooleanBinding usedByRun = inUse.byRun(viewModel.editedProviderId());
        return Bindings.createBooleanBinding(
                () -> !viewModel.adding().get() && (usedByRun.get() || (blocker.get() != null && isEditingCurrent())),
                blocker,
                usedByRun,
                viewModel.adding(),
                viewModel.editedProviderId(),
                viewModel.selectedProviderId());
    }

    private boolean isEditingCurrent() {
        return viewModel
                .editedProviderId()
                .get()
                .equals(viewModel.selectedProviderId().get());
    }

    private void bindEmptyState() {
        Visibility.bind(providerEmpty, Bindings.isEmpty(viewModel.providers()));
        Visibility.bind(formCard, Bindings.isNotEmpty(viewModel.providers()).or(viewModel.adding()));
    }

    private void bindFooter() {
        Visibility.bind(formDirty, form.dirty());
        Visibility.bind(formSaved, form.justSaved());
        formCancel
                .disableProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> !form.dirty().get() && !viewModel.adding().get(), form.dirty(), viewModel.adding()));
        formSave.disableProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> !form.dirty().get() || form.saving().get(), form.dirty(), form.saving()));
    }

    private void onProvidersLoaded() {
        showProviders();
        viewModel.refreshModels();
    }

    private void showProviders() {
        rowList.show();
    }

    private void onTabChanged(final Tab was, final Tab now) {
        if (movingTab || now == null) {
            return;
        }
        if (was != null && guard.holdsChanges(was)) {
            log.debug("tab change vetoed: the form of {} holds unsaved changes", was.getId());
            select(was);
            guard.guardTab(was, () -> moveTo(now));
            return;
        }
        entered(now);
    }

    private void moveTo(final Tab tab) {
        select(tab);
        entered(tab);
    }

    private void entered(final Tab tab) {
        tabEntries.entered(tab.getId());
    }

    private void select(final Tab tab) {
        movingTab = true;
        try {
            tabs.getSelectionModel().select(tab);
        } finally {
            movingTab = false;
        }
    }

    private void askDelete() {
        final String id = viewModel.editedProviderId().get();
        final String name = viewModel.providerName(id);
        log.debug("delete of provider '{}' asked", id);
        confirm.ask(
                ConfirmPrompt.Question.DELETE_PROVIDER, List.of(name), List.of(name), () -> form.delete(id, () -> {}));
    }

    private void onFocusRequested(final ProviderForm.Spot spot) {
        if (spot == ProviderForm.Spot.MODEL) {
            scrollTo(formView.model().combo());
        }
    }

    private void scrollTo(final Node target) {
        Platform.runLater(() -> {
            final Node content = formScroll.getContent();
            final double height = content.getBoundsInLocal().getHeight();
            final double view = formScroll.getViewportBounds().getHeight();
            if (height > view) {
                final double top = target.localToScene(target.getBoundsInLocal())
                                .getMinY()
                        - content.localToScene(content.getBoundsInLocal()).getMinY();
                formScroll.setVvalue(Math.min(1, Math.max(0, top / (height - view))));
            }
        });
    }

    private void open(final String id) {
        if (!viewModel.adding().get() && id.equals(viewModel.editedProviderId().get())) {
            log.debug("provider '{}' is already open", id);
            return;
        }
        guard.guardProvider(() -> viewModel.editProvider(id));
    }
}
