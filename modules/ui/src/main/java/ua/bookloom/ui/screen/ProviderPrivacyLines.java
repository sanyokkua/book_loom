package ua.bookloom.ui.screen;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.LoopbackHosts;
import ua.bookloom.ui.state.ProviderDraft;
import ua.bookloom.ui.state.ProviderForm;

/**
 * Where the open provider's text goes: a provider on this computer keeps the book private, any other is named by its
 * host, and LM Studio's own server log is mentioned under it. Nothing is asked: there is no consent dialog or box. The
 * form outlives this view, so it is followed through a weak listener held here.
 */
@Slf4j
final class ProviderPrivacyLines {

    private final ProviderForm form;
    private final Messages messages;
    private final Label privacy = Wrapped.label("", "hint");
    private final Label lmStudioLog = Wrapped.label("", "hint");
    private final VBox box = new VBox(4, privacy, lmStudioLog);
    private final InvalidationListener onDraft = observed -> show();

    ProviderPrivacyLines(final ProviderForm form, final Messages messages) {
        this.form = Objects.requireNonNull(form, "form");
        this.messages = Objects.requireNonNull(messages, "messages");
        box.setId("settings-privacy");
        privacy.setId("settings-privacy-line");
        lmStudioLog.setId("settings-privacy-lmstudio-log");
        lmStudioLog.setText(messages.get(MessageKey.SETTINGS_LMSTUDIO_LOG));
        form.draft().addListener(new WeakInvalidationListener(onDraft));
        show();
    }

    VBox node() {
        return box;
    }

    private void show() {
        final ProviderDraft draft = form.draft().get();
        final Optional<String> host = hostOf(draft.address());
        final boolean remote = host.isPresent() && !LoopbackHosts.isLoopback(host.get());
        privacy.setText(
                remote
                        ? messages.get(MessageKey.SETTINGS_PRIVACY_REMOTE, host.get())
                        : messages.get(MessageKey.SETTINGS_PRIVACY_LOCAL));
        Visibility.show(lmStudioLog, draft.engine().effective() == ServerEngine.LM_STUDIO);
    }

    // The address as typed may not parse yet; until it does, nothing names a host.
    private static Optional<String> hostOf(final String address) {
        try {
            return Optional.ofNullable(URI.create(address.strip()).getHost());
        } catch (IllegalArgumentException unparsable) {
            log.debug("privacy line: the address does not parse yet, no host is named");
            return Optional.empty();
        }
    }
}
