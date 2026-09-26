package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedModelCatalog;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The life of a model listing: what it publishes, how each failure is routed, what is discarded, and how an answer that
 * no longer matches the request is dropped.
 */
class ModelListingTest extends SettingsViewModelTestBase {

    private static final RecordingToasts.Raised UNREADABLE_WARNING =
            new RecordingToasts.Raised("warning", MessageKey.TOAST_MODEL_LIST_UNREADABLE, List.of());

    private void refresh(final ModelListing listing, final String providerId) {
        onFx(() -> {
            listing.refresh(providerId);
            return null;
        });
    }

    private void refreshAndAwait(final ModelListing listing, final String providerId) {
        refresh(listing, providerId);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static boolean isListing(final ModelListing listing) {
        return onFx(() -> listing.listing().get());
    }

    private static boolean noListObtained(final ModelListing listing) {
        return onFx(() -> listing.noListObtained().get());
    }

    // IF the offered models were reordered, dropped or left flagged as failed, THEN a person would pick from a list
    // that is not what the server reports.
    @Test
    void refresh_catalogueAnswers_publishesIdsInOrderAndClearsListing() {
        catalog = ScriptedModelCatalog.returning("gemma3:12b", "qwen3:8b");
        final ModelListing listing = newModelListing();

        refreshAndAwait(listing, "ollama");

        assertThat(offered(listing)).containsExactly("gemma3:12b", "qwen3:8b");
        assertThat(isListing(listing)).isFalse();
        assertThat(noListObtained(listing)).isFalse();
        assertThat(onFx(() -> listing.refusal().get())).isEmpty();
        assertThat(toasts.raised()).isEmpty();
        assertThat(errors.presented()).isEmpty();
    }

    // IF the listing were asked of another provider than the one named, THEN the person would be offered another
    // server's models.
    @Test
    void refresh_providerId_asksTheCatalogueForThatId() {
        final ModelListing listing = newModelListing();

        refreshAndAwait(listing, "lmstudio");

        assertThat(catalog.providerIds()).containsExactly("lmstudio");
    }

    // IF a listing failure a person can live with opened a dialog, or raised no note, or raised it twice, THEN merely
    // opening settings with the server off would raise a modal, or the missing list would go unexplained.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"discoveryFailed", "unreachable", "timeout", "auth"})
    void refresh_readableFailure_flagsNoListWithOneWarningAndNoDialog(final ErrorCode code) {
        catalog = ScriptedModelCatalog.failing(AppError.of(code, "Listing failed", "The list could not be read."));
        final ModelListing listing = newModelListing();

        refreshAndAwait(listing, "ollama");

        assertThat(noListObtained(listing)).isTrue();
        assertThat(toasts.raised()).containsExactly(UNREADABLE_WARNING);
        assertThat(errors.presented()).isEmpty();
        assertThat(offered(listing)).isEmpty();
        assertThat(isListing(listing)).isFalse();
    }

    // IF an unexpected or busy refusal were only noted in place, THEN it would lack the expandable-details dialog the
    // notification spec gives it, and a warning toast would misreport it as a merely unreadable list.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"internal", "busy"})
    void refresh_internalOrBusy_goesToThePresenterWithNoToast(final ErrorCode code) {
        final AppError refusal = AppError.of(code, "Refused", "The listing was refused.");
        catalog = ScriptedModelCatalog.failing(refusal);
        final ModelListing listing = newModelListing();

        refreshAndAwait(listing, "ollama");

        assertThat(errors.presented()).containsExactly(refusal);
        assertThat(toasts.raised()).isEmpty();
        assertThat(isListing(listing)).isFalse();
    }

    // IF a refusal a person can fix opened a dialog or a toast, THEN the specified in-place treatment would be
    // missing; it must be said beside the control, in the refusal's own words.
    @Test
    void refresh_validationRefusal_isSaidInPlaceWithNoToastOrDialog() {
        catalog = ScriptedModelCatalog.failing(
                AppError.of(ErrorCode.validation, "Unknown provider", "No such provider is registered."));
        final ModelListing listing = newModelListing();

        refreshAndAwait(listing, "ollama");

        assertThat(onFx(() -> listing.refusal().get())).isEqualTo("No such provider is registered.");
        assertThat(toasts.raised()).isEmpty();
        assertThat(errors.presented()).isEmpty();
        assertThat(isListing(listing)).isFalse();
    }

    // IF a cancelled listing raised a message, THEN a person who left the screen would be told about work they
    // abandoned.
    @Test
    void refresh_cancelled_isSilent() {
        catalog = ScriptedModelCatalog.failing(AppError.of(ErrorCode.cancelled, "Cancelled", "The listing stopped."));
        final ModelListing listing = newModelListing();

        refreshAndAwait(listing, "ollama");

        assertThat(toasts.raised()).isEmpty();
        assertThat(errors.presented()).isEmpty();
        assertThat(onFx(() -> listing.refusal().get())).isEmpty();
        assertThat(isListing(listing)).isFalse();
    }

    // IF a server that holds no models were presented as an empty choice with no explanation, THEN the empty list
    // would read as the whole truth; but it is a normal state, so it must not also raise a toast.
    @Test
    void refresh_emptyList_flagsNoListWithoutAToast() {
        final ModelListing listing = newModelListing();

        refreshAndAwait(listing, "ollama");

        assertThat(noListObtained(listing)).isTrue();
        assertThat(offered(listing)).isEmpty();
        assertThat(toasts.raised()).isEmpty();
        assertThat(errors.presented()).isEmpty();
    }

    // IF the no-list flag or a refusal stayed after a later listing succeeded, THEN the screen would keep saying no
    // list was obtained beside a populated list.
    @Test
    void refresh_successAfterFailure_clearsNoListFlagAndRefusal() {
        catalog = ScriptedModelCatalog.failing(AppError.of(ErrorCode.unreachable, "Unreachable", "Nothing answered."));
        final ModelListing listing = newModelListing();
        refreshAndAwait(listing, "ollama");
        catalog.respondWith(AppError.of(ErrorCode.validation, "Unknown provider", "No such provider."));
        refreshAndAwait(listing, "ollama");
        assertThat(onFx(() -> listing.refusal().get())).isNotEmpty();
        catalog.respondWith("gemma3:12b");

        refreshAndAwait(listing, "ollama");

        assertThat(noListObtained(listing)).isFalse();
        assertThat(onFx(() -> listing.refusal().get())).isEmpty();
        assertThat(offered(listing)).containsExactly("gemma3:12b");
    }

    // IF a listing that failed threw away the models a person was already choosing from, THEN one flaky answer would
    // empty a list they could still use.
    @Test
    void refresh_laterListingFails_keepsThePreviouslyOfferedIds() {
        catalog = ScriptedModelCatalog.returning("gemma3:12b");
        final ModelListing listing = newModelListing();
        refreshAndAwait(listing, "ollama");
        catalog.respondWith(AppError.of(ErrorCode.timeout, "Timed out", "The server did not answer."));

        refreshAndAwait(listing, "ollama");

        assertThat(offered(listing)).containsExactly("gemma3:12b");
    }

    // IF a defect in the catalogue escaped as an exception, THEN it would crash the FX thread or vanish and leave the
    // listing mark set for good; it must surface as an internal error instead.
    @Test
    void refresh_catalogueThrows_clearsListingAndPresentsInternalError() {
        catalog = ScriptedModelCatalog.throwing(new IllegalStateException("adapter defect"));
        final ModelListing listing = newModelListing();

        refreshAndAwait(listing, "ollama");

        assertThat(errors.presented()).extracting(AppError::code).containsExactly(ErrorCode.internal);
        assertThat(toasts.raised()).isEmpty();
        assertThat(isListing(listing)).isFalse();
    }

    // IF asking again for the provider already being listed sent a second request, THEN every visit to the screen
    // during a slow answer would stack another request on the server.
    @Test
    void refresh_sameProviderAlreadyInFlight_doesNotAskAgain() throws InterruptedException, TimeoutException {
        catalog = ScriptedModelCatalog.gated("gemma3:12b");
        final CountingPool pool = useCountingPool();
        final ModelListing listing = newModelListing();
        refresh(listing, "ollama");
        catalog.awaitEntered();

        refresh(listing, "ollama");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(isListing(listing)).isTrue();
        assertThat(catalog.callCount()).isEqualTo(1);
        catalog.release();
        awaitNotListing(listing);
        pool.awaitFinished(1);
    }

    // IF discarding left anything of the previous provider on screen, THEN a person would see one server's list, note
    // or refusal under another's name.
    @Test
    void discard_afterAnAnswer_clearsListNoteAndRefusal() {
        catalog = ScriptedModelCatalog.returning("gemma3:12b");
        final ModelListing listing = newModelListing();
        refreshAndAwait(listing, "ollama");
        catalog.respondWith(AppError.of(ErrorCode.validation, "Unknown provider", "No such provider."));
        refreshAndAwait(listing, "ollama");
        catalog.respondWith(AppError.of(ErrorCode.unreachable, "Unreachable", "Nothing answered."));
        refreshAndAwait(listing, "ollama");
        assertThat(noListObtained(listing)).isTrue();

        onFx(() -> {
            listing.discard();
            return null;
        });

        assertThat(offered(listing)).isEmpty();
        assertThat(noListObtained(listing)).isFalse();
        assertThat(onFx(() -> listing.refusal().get())).isEmpty();
        assertThat(isListing(listing)).isFalse();
    }

    // IF an answer that was in flight when the listing was discarded were still published, THEN the discarded list
    // would come back.
    @Test
    void discard_answerInFlight_isDroppedWithNoNote() throws InterruptedException, TimeoutException {
        catalog = ScriptedModelCatalog.gated("stale-model");
        final CountingPool pool = useCountingPool();
        final ModelListing listing = newModelListing();
        refresh(listing, "ollama");
        catalog.awaitEntered();
        onFx(() -> {
            listing.discard();
            return null;
        });
        assertThat(isListing(listing)).isFalse();

        catalog.release();
        pool.awaitFinished(1);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(offered(listing)).isEmpty();
        assertThat(isListing(listing)).isFalse();
        assertThat(toasts.raised()).isEmpty();
    }

    // IF an answer for the provider asked earlier were published after another provider was asked, THEN the screen
    // would offer one server's models under another's name.
    @Test
    void refresh_answerForPreviousProviderArrivesLate_isDropped() throws InterruptedException {
        catalog = ScriptedModelCatalog.gated("stale-model");
        final CountingPool pool = useCountingPool();
        final ModelListing listing = newModelListing();
        refresh(listing, "ollama");
        catalog.awaitEntered();
        catalog.respondWith("lm-model");

        refresh(listing, "lmstudio");
        catalog.awaitCalls(2);
        pool.awaitFinished(1);
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(offered(listing)).containsExactly("lm-model");

        catalog.release();
        pool.awaitFinished(1);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(offered(listing)).containsExactly("lm-model");
        assertThat(isListing(listing)).isFalse();
        assertThat(catalog.providerIds()).containsExactly("ollama", "lmstudio");
    }

    // IF a stale answer clearing the in-progress mark also cleared it for the newer request still in flight, THEN the
    // screen would stop saying the list is being fetched while it still is.
    @Test
    void refresh_staleAnswerArrivesWhileNewerRequestInFlight_keepsListingTrue()
            throws InterruptedException, TimeoutException {
        catalog = ScriptedModelCatalog.gated("stale-model");
        final CountingPool pool = useCountingPool();
        final ModelListing listing = newModelListing();
        refresh(listing, "ollama");
        catalog.awaitEntered();
        catalog.respondWith("lm-model");
        catalog.hold();
        onFx(() -> {
            listing.discard();
            return null;
        });
        refresh(listing, "lmstudio");
        catalog.awaitCalls(2);

        catalog.release();
        pool.awaitFinished(1);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(isListing(listing)).isTrue();
        assertThat(offered(listing)).isEmpty();

        catalog.release();
        awaitNotListing(listing);
        pool.awaitFinished(1);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(offered(listing)).containsExactly("lm-model");
    }

    private static void awaitNotListing(final ModelListing listing) throws TimeoutException {
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> !isListing(listing));
    }
}
