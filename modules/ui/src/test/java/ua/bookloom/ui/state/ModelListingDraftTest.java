package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.ui.ScriptedModelCatalog;

/** Listing the models of a form that is not saved asks the description, never an id nobody registered. */
class ModelListingDraftTest extends SettingsViewModelTestBase {

    private static final ProviderConfig OFFICE = new ProviderConfig(
            "new-provider",
            ProviderKind.OPENAI_COMPATIBLE,
            URI.create("http://10.0.0.5:1234/v1"),
            ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
            ProviderConfig.DEFAULT_REQUEST_TIMEOUT);

    private void refresh(final ModelListing listing, final ProviderConfig draft) {
        onFx(() -> {
            listing.refresh(draft);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF the draft listing asked by id, THEN a provider that is not saved would be refused as unknown.
    @Test
    void refresh_draft_asksTheCatalogueAboutTheDescription() {
        catalog = ScriptedModelCatalog.returning("qwen3:8b", "gemma3:12b");
        final ModelListing listing = newModelListing();

        refresh(listing, OFFICE);

        assertThat(catalog.drafts()).containsExactly(OFFICE);
        assertThat(offered(listing)).containsExactly("qwen3:8b", "gemma3:12b");
    }

    // IF a second tap stacked a request on a slow server, THEN revisiting the form would pile them up.
    @Test
    void refresh_sameDraftInFlight_isNotAskedAgain() throws InterruptedException {
        catalog = ScriptedModelCatalog.gated("qwen3:8b");
        useCountingPool();
        final ModelListing listing = newModelListing();
        onFx(() -> {
            listing.refresh(OFFICE);
            listing.refresh(OFFICE);
            return null;
        });
        catalog.awaitEntered();
        catalog.release();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(catalog.drafts()).hasSize(1);
        assertThat(offered(listing)).containsExactly("qwen3:8b");
    }

    // IF another address were taken for the one in flight, THEN the answer of the old server would fill the list.
    @Test
    void refresh_otherDraftWhileInFlight_isAskedAnew() throws InterruptedException {
        catalog = ScriptedModelCatalog.gated("qwen3:8b");
        useCountingPool();
        final ModelListing listing = newModelListing();
        final ProviderConfig other = new ProviderConfig(
                "new-provider",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("http://10.0.0.6:1234/v1"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);
        onFx(() -> {
            listing.refresh(OFFICE);
            return null;
        });
        catalog.awaitEntered();

        onFx(() -> {
            listing.refresh(other);
            return null;
        });
        catalog.release();
        catalog.awaitCalls(2);

        assertThat(catalog.drafts()).isEqualTo(List.of(OFFICE, other));
    }
}
