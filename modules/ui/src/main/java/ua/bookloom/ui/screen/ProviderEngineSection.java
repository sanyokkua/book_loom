package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.EngineWords;
import ua.bookloom.ui.state.ProviderDraft;
import ua.bookloom.ui.state.ProviderEngine;
import ua.bookloom.ui.state.ProviderForm;
import ua.bookloom.ui.state.Tone;

/**
 * What sits under the address: the detected-engine line with Detect again beside it, why a detection got no answer,
 * and the one-time offer of Ollama's own connection. The engine state outlives this view, so it is followed through
 * weak listeners held here.
 */
final class ProviderEngineSection {

    private static final double GAP = 8;
    private static final double ROW_GAP = 4;

    private final ProviderForm form;
    private final ProviderEngine engine;
    private final Messages messages;
    private final Label line = new Label();
    private final Label asking = new Label();
    private final Label unanswered = new Label();
    private final Label unslothKey = Wrapped.label("", "hint");
    private final Label unslothTools = Wrapped.label("", "hint");
    private final VBox unslothHelp = new VBox(ROW_GAP, unslothKey, unslothTools);
    private final Banner offer;
    private final VBox box = new VBox(ROW_GAP);
    private final InvalidationListener onChange = observed -> show();

    ProviderEngineSection(final ProviderForm form, final ProviderEngine engine, final Messages messages) {
        this.form = Objects.requireNonNull(form, "form");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.messages = Objects.requireNonNull(messages, "messages");
        offer = new Banner("settings-engine-offer", Tone.INFO, "i", "", messages.get(MessageKey.SETTINGS_OLLAMA_OFFER));
        box.setId("settings-engine");
        box.getChildren().addAll(lineRow(), unansweredNote(), offerBanner(), unslothHelp());
        form.draft().addListener(new WeakInvalidationListener(onChange));
        engine.detection().addListener(new WeakInvalidationListener(onChange));
        engine.lineShown().addListener(new WeakInvalidationListener(onChange));
        show();
    }

    VBox node() {
        return box;
    }

    private FlowPane lineRow() {
        line.setId("settings-engine-line");
        line.getStyleClass().add("hint");
        line.setMinWidth(Region.USE_PREF_SIZE);
        final Button detect = new Button(messages.get(MessageKey.SETTINGS_ENGINE_DETECT));
        detect.setId("settings-engine-detect");
        detect.getStyleClass().add("btn-ghost");
        detect.setMinWidth(Region.USE_PREF_SIZE);
        Tips.install(messages, detect, MessageKey.SETTINGS_ENGINE_DETECT_TIP);
        detect.disableProperty().bind(engine.detecting());
        detect.setOnAction(event -> engine.detectAgain());
        asking.setId("settings-engine-asking");
        asking.setText(messages.get(MessageKey.SETTINGS_ENGINE_DETECTING));
        asking.getStyleClass().add("muted");
        asking.setMinWidth(Region.USE_PREF_SIZE);
        Visibility.bind(asking, engine.detecting());
        final FlowPane row = new FlowPane(GAP, ROW_GAP, line, detect, asking);
        row.setId("settings-engine-row");
        Visibility.bind(row, engine.lineShown());
        return row;
    }

    private Label unansweredNote() {
        unanswered.setId("settings-engine-unanswered");
        unanswered.setText(messages.get(MessageKey.SETTINGS_ENGINE_UNANSWERED));
        unanswered.getStyleClass().add("hint-warn");
        Visibility.bind(unanswered, engine.unanswered());
        return Wrapped.wholeLines(unanswered);
    }

    private Banner offerBanner() {
        final Button use = button("settings-engine-offer-use", MessageKey.SETTINGS_OLLAMA_OFFER_USE, "btn-primary");
        Tips.install(messages, use, MessageKey.SETTINGS_OLLAMA_OFFER_USE_TIP);
        use.setOnAction(event -> engine.useNative());
        final Button keep =
                button("settings-engine-offer-keep", MessageKey.SETTINGS_OLLAMA_OFFER_KEEP, "btn-secondary");
        Tips.install(messages, keep, MessageKey.SETTINGS_OLLAMA_OFFER_KEEP_TIP);
        keep.setOnAction(event -> engine.keepAsIs());
        offer.addDetail(new FlowPane(GAP, ROW_GAP, use, keep));
        Visibility.bind(offer, engine.offered());
        return offer;
    }

    // Unsloth needs a key made in its own settings and has chat tools of its own; both are said where the engine is.
    private VBox unslothHelp() {
        unslothKey.setId("settings-unsloth-key-hint");
        unslothKey.setText(messages.get(MessageKey.SETTINGS_UNSLOTH_KEY_HINT));
        unslothTools.setId("settings-unsloth-tools");
        unslothTools.setText(messages.get(MessageKey.SETTINGS_UNSLOTH_TOOLS));
        unslothHelp.setId("settings-unsloth-help");
        return unslothHelp;
    }

    private Button button(final String id, final MessageKey text, final String style) {
        final Button button = new Button(messages.get(text));
        button.setId(id);
        button.getStyleClass().add(style);
        button.setMinWidth(Region.USE_PREF_SIZE);
        return button;
    }

    private void show() {
        final ProviderDraft draft = form.draft().get();
        line.setText(EngineWords.line(messages, draft.kind(), draft.engine()));
        Visibility.show(unslothHelp, draft.engine().effective() == ServerEngine.UNSLOTH);
    }
}
