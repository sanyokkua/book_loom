package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ReviewEditor;

/**
 * The banner beside the review actions after a save was refused for its formatting tokens: it names the tokens the
 * edit lacks as chips that insert one at the cursor, and those it holds too often. It is gone as soon as the text holds
 * every token again, so the person sees exactly what stands between the edit and Save.
 */
@Slf4j
final class ReviewTokenBanner extends VBox {

    private static final double SPACING = 6;

    private final Messages messages;
    private final TextArea target;
    private final FlowPane chips = new FlowPane(SPACING, SPACING);
    private final Label needed = new Label();
    private final ListChangeListener<String> onNeeded = change -> rebuild(change.getList());

    ReviewTokenBanner(final ReviewEditor editor, final TextArea target, final Messages messages) {
        super(SPACING);
        this.messages = Objects.requireNonNull(messages, "messages");
        this.target = Objects.requireNonNull(target, "target");
        setId("review-token-banner");
        getStyleClass().addAll("banner", "banner-warn");
        needed.setText(messages.get(MessageKey.REVIEW_TOKENS_NEEDED));
        needed.getStyleClass().add("banner-text");
        needed.setWrapText(true);
        needed.setMinHeight(Region.USE_PREF_SIZE);
        chips.setId("review-token-chips");
        final Label extra = new Label();
        extra.setId("review-token-extra");
        extra.getStyleClass().add("banner-text");
        extra.setWrapText(true);
        extra.setMinHeight(Region.USE_PREF_SIZE);
        extra.textProperty().bind(editor.extraTokens().map(tokens -> tokens.isEmpty() ? "" : wording(tokens)));
        extra.visibleProperty().bind(editor.extraTokens().isNotEmpty());
        extra.managedProperty().bind(extra.visibleProperty());
        getChildren().addAll(needed, chips, extra);
        editor.neededTokens().addListener(new WeakListChangeListener<>(onNeeded));
        visibleProperty()
                .bind(Bindings.isNotEmpty(editor.neededTokens())
                        .or(editor.extraTokens().isNotEmpty()));
        managedProperty().bind(visibleProperty());
        rebuild(editor.neededTokens());
    }

    private String wording(final String tokens) {
        return messages.get(MessageKey.REVIEW_TOKENS_EXTRA, tokens);
    }

    private void rebuild(final List<? extends String> tokens) {
        log.debug("token banner shows {} needed tokens", tokens.size());
        needed.setVisible(!tokens.isEmpty());
        needed.setManaged(!tokens.isEmpty());
        chips.getChildren().setAll(tokens.stream().map(this::chip).toList());
    }

    private Button chip(final String token) {
        final Button chip = new Button(token);
        chip.getStyleClass().addAll("chip", "chip-warn");
        chip.setAccessibleText(messages.get(MessageKey.REVIEW_TOKEN_INSERT, token));
        Tips.install(chip, messages.get(MessageKey.REVIEW_TOKEN_INSERT_TIP, token));
        chip.setOnAction(event -> {
            log.debug("token chip {} inserts it at the cursor", token);
            target.insertText(target.getCaretPosition(), token);
            target.requestFocus();
        });
        return chip;
    }
}
