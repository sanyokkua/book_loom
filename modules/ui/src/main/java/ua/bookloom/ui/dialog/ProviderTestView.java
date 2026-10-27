package ua.bookloom.ui.dialog;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.beans.InvalidationListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.notify.Severity;
import ua.bookloom.ui.state.CheckSentences;
import ua.bookloom.ui.state.ProviderTest;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.StageChip;
import ua.bookloom.ui.state.TestDetail;
import ua.bookloom.ui.state.TestDetails;
import ua.bookloom.ui.state.TestSubject;
import ua.bookloom.ui.state.Tone;

/** The card of {@link ProviderTestDialog}: it draws one {@link TestDetail} and redraws it when the runner changes. */
@Slf4j
final class ProviderTestView {

    private static final double BODY_SPACING = 12;
    private static final double RAW_HEIGHT = 360;
    private static final double METRICS_WIDTH = 230;
    private static final double COLUMN_SPACING = 14;

    private final Messages messages;
    private final SettingsViewModel viewModel;
    private final TextClipboard clipboard;
    private final Runnable close;
    private final ModalCard card = new ModalCard(ModalCard.Size.XL);
    private final Label title = ModalCard.title("");
    private final Banner verdict = new Banner("test-verdict", Tone.INFO, Messages.ELLIPSIS, "", "");
    private final Label requestLine = new Label();
    private final Button copyCurl = new Button();
    private final HBox requestRow = new HBox(10);
    private final TextArea body = raw("test-body");
    private final TextArea response = raw("test-response");
    private final Button copyBody = new Button();
    private final Button copyResponse = new Button();
    private final VBox metrics = new VBox();
    private final Map<VerificationStage, Button> tabs = new EnumMap<>(VerificationStage.class);
    private final InvalidationListener onRunner = observed -> refresh();
    private VerificationStage stage;
    private TestDetail detail = new TestDetail(TestDetail.Severity.INFO, "", "", "", "", "", List.of(), "", "");

    ProviderTestView(
            final Messages messages,
            final SettingsViewModel viewModel,
            final TextClipboard clipboard,
            final VerificationStage stage,
            final Runnable close) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        this.stage = Objects.requireNonNull(stage, "stage");
        this.close = Objects.requireNonNull(close, "close");
        build();
        viewModel.tests().stages().addListener(onRunner);
        viewModel.tests().running().addListener(onRunner);
        card.sceneProperty().addListener((observed, was, now) -> {
            if (now == null && was != null) {
                detach();
            }
        });
        refresh();
    }

    ModalCard card() {
        return card;
    }

    private void detach() {
        log.debug("the test dialog was closed: no longer following the runner");
        viewModel.tests().stages().removeListener(onRunner);
        viewModel.tests().running().removeListener(onRunner);
    }

    private static TextArea raw(final String id) {
        final TextArea area = new TextArea();
        area.setId(id);
        area.setEditable(false);
        area.setWrapText(false);
        area.getStyleClass().add("test-raw");
        area.setPrefHeight(RAW_HEIGHT);
        area.setMinHeight(RAW_HEIGHT);
        return area;
    }

    private void build() {
        card.setId("test-card");
        card.header(title, Wrapped.label(messages.get(MessageKey.SETTINGS_TEST_SUBTITLE), "dialog-sub"));
        buildRequestRow();
        final HBox columns = new HBox(
                COLUMN_SPACING,
                pane(MessageKey.SETTINGS_TEST_BODY, body, copyBody, "test-copy-body", () -> detail.rawBody()),
                metricsPane(),
                pane(
                        MessageKey.SETTINGS_TEST_RESPONSE,
                        response,
                        copyResponse,
                        "test-copy-response",
                        () -> detail.rawResponse()));
        columns.getStyleClass().add("test-columns");
        card.setContent(new VBox(BODY_SPACING, verdict, requestRow, columns));
        buildFooter();
    }

    private void buildRequestRow() {
        requestLine.setId("test-request-line");
        requestLine.getStyleClass().add("test-request-line");
        requestLine.setMinWidth(0);
        HBox.setHgrow(requestLine, Priority.ALWAYS);
        requestLine.setMaxWidth(Double.MAX_VALUE);
        copyCurl.setId("test-copy-curl");
        copyCurl.setText(messages.get(MessageKey.SETTINGS_TEST_COPY_CURL));
        copyCurl.getStyleClass().add("btn-secondary");
        Tips.install(messages, copyCurl, MessageKey.SETTINGS_TEST_COPY_CURL_TIP);
        copyCurl.setOnAction(event -> copy(detail.curl(), MessageKey.SETTINGS_TEST_COPIED_CURL));
        requestRow.getChildren().addAll(requestLine, copyCurl);
    }

    private VBox pane(
            final MessageKey head,
            final TextArea area,
            final Button copy,
            final String copyId,
            final Supplier<String> raw) {
        final Label name = new Label(messages.get(head));
        name.getStyleClass().add("card-title");
        copy.setId(copyId);
        copy.setText(messages.get(MessageKey.SETTINGS_TEST_COPY));
        copy.getStyleClass().add("btn-ghost");
        Tips.install(messages, copy, MessageKey.SETTINGS_TEST_COPY_TIP);
        copy.setOnAction(event -> copy(raw.get(), MessageKey.SETTINGS_TEST_COPIED));
        final Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        final HBox top = new HBox(8, name, grow, copy);
        top.getStyleClass().add("test-pane-head");
        final VBox pane = new VBox(6, top, area);
        pane.getStyleClass().add("test-pane");
        HBox.setHgrow(pane, Priority.ALWAYS);
        pane.setMinWidth(0);
        pane.setPrefWidth(0);
        return pane;
    }

    private VBox metricsPane() {
        metrics.setId("test-metrics");
        final VBox pane = new VBox(6, metrics);
        pane.getStyleClass().add("test-pane");
        pane.setMinWidth(METRICS_WIDTH);
        pane.setPrefWidth(METRICS_WIDTH);
        pane.setMaxWidth(METRICS_WIDTH);
        return pane;
    }

    private void buildFooter() {
        card.getButtonTypes().clear();
        for (final VerificationStage each : VerificationStage.values()) {
            final ButtonType type = new ButtonType(CheckSentences.stageName(messages, each), ButtonBar.ButtonData.LEFT);
            card.getButtonTypes().add(type);
            final Button tab = (Button) card.lookupButton(type);
            tab.setId("test-tab-" + each.name().toLowerCase(Locale.ROOT));
            Tips.install(messages, tab, tabTip(each));
            tab.setOnAction(event -> show(each));
            tabs.put(each, tab);
        }
        Tips.install(
                        messages,
                        card.button(
                                messages.get(MessageKey.SETTINGS_TEST_CLOSE),
                                ButtonBar.ButtonData.OK_DONE,
                                "test-close",
                                "btn-primary"),
                        MessageKey.SETTINGS_TEST_CLOSE_TIP)
                .setOnAction(event -> close.run());
        card.setOnEscape(close);
    }

    private static MessageKey tabTip(final VerificationStage which) {
        return switch (which) {
            case CONNECTION -> MessageKey.SETTINGS_STAGE_CONNECTION_TIP;
            case MODELS -> MessageKey.SETTINGS_STAGE_MODELS_TIP;
            case INFERENCE -> MessageKey.SETTINGS_STAGE_INFERENCE_TIP;
        };
    }

    private void show(final VerificationStage next) {
        log.debug("the test dialog now shows {}", next);
        stage = next;
        card.clearFeedback();
        refresh();
    }

    private void copy(final String text, final MessageKey said) {
        log.debug("copying {} characters from the test dialog", text.length());
        clipboard.copy(text);
        card.feedback(messages.get(said), Severity.SUCCESS);
    }

    private void refresh() {
        final ProviderTest runningTest = viewModel.tests().running().get();
        final boolean running = runningTest != null && stage.compareTo(runningTest.lastStage()) <= 0;
        final List<StageChip> chips = viewModel.tests().stages();
        detail = TestDetails.of(
                messages,
                stage,
                chips,
                running,
                viewModel.testedName(),
                viewModel.testedSubject().get().map(TestSubject::isLocalProgram).orElse(false),
                viewModel
                        .testedSubject()
                        .get()
                        .map(tested -> !tested.model().isBlank())
                        .orElse(false));
        log.debug("test dialog redrawn for {}: {}, running {}", stage, detail.severity(), running);
        title.setText(messages.get(titleOf(stage)));
        showVerdict();
        requestLine.setText(detail.requestLine());
        requestRow.setVisible(!detail.requestLine().isEmpty());
        requestRow.setManaged(requestRow.isVisible());
        body.setText(detail.body());
        response.setText(detail.response());
        copyBody.setDisable(detail.rawBody().isEmpty());
        copyResponse.setDisable(detail.rawResponse().isEmpty());
        showMetrics();
        showTabs(chips);
    }

    private static MessageKey titleOf(final VerificationStage which) {
        return switch (which) {
            case CONNECTION -> MessageKey.SETTINGS_TEST_CONNECTION;
            case MODELS -> MessageKey.SETTINGS_TEST_MODELS;
            case INFERENCE -> MessageKey.SETTINGS_TEST_INFERENCE;
        };
    }

    private void showVerdict() {
        verdict.setRole(
                switch (detail.severity()) {
                    case OK -> Tone.OK;
                    case WARN -> Tone.WARN;
                    case ERR -> Tone.ERR;
                    case INFO -> Tone.INFO;
                });
        verdict.setGlyph(
                switch (detail.severity()) {
                    case OK -> "✓";
                    case WARN -> "!";
                    case ERR -> "✕";
                    case INFO -> Messages.ELLIPSIS;
                });
        verdict.setText(detail.verdict());
    }

    private void showMetrics() {
        final List<Node> rows = detail.metrics().stream().map(this::metricRow).toList();
        metrics.getChildren().setAll(rows);
    }

    private Node metricRow(final TestDetail.Metric metric) {
        final Label key = new Label(metric.label());
        key.getStyleClass().add("kv-key");
        final Label value = new Label(metric.value());
        value.setId("test-metric-" + metric.name());
        value.getStyleClass().add("kv-value");
        value.setWrapText(true);
        final HBox row = new HBox(8, key, value);
        row.getStyleClass().add("kv");
        return row;
    }

    private void showTabs(final List<StageChip> chips) {
        tabs.forEach((each, tab) -> {
            final boolean ran = chips.stream().anyMatch(chip -> chip.stage() == each);
            // A stage never tried because an earlier one failed still opens, to say so.
            final boolean told = ran || TestDetails.failedBefore(each, chips).isPresent();
            tab.setDisable(!told && each != stage);
            tab.getStyleClass().removeAll("btn-secondary", "btn-ghost");
            tab.getStyleClass().add(each == stage ? "btn-secondary" : "btn-ghost");
        });
    }
}
