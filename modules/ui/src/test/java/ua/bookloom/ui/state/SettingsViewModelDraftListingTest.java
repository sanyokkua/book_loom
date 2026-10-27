package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ScriptedModelCatalog;

/** The refresh beside the Model field lists the server the form describes, not the one that is saved. */
class SettingsViewModelDraftListingTest extends ProviderFormTestBase {

    // IF Refresh asked for the saved address, THEN a person fixing a wrong address could not see the fix work.
    @Test
    void refreshModels_addressEdited_listsTheDescriptionInTheForm() {
        catalog = ScriptedModelCatalog.returning("qwen3:8b");
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        edit(form, draft -> draft.withAddress("http://localhost:11500"));

        refreshAndAwait(viewModel);

        assertThat(catalog.drafts())
                .extracting(config -> config.baseUrl().toString())
                .contains("http://localhost:11500");
    }

    // IF Refresh worked only for saved providers, THEN a new provider could not show its models before Save.
    @Test
    void refreshModels_whileAdding_listsTheDescriptionUnderTheDraftId() {
        catalog = ScriptedModelCatalog.returning("qwen3:8b");
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        addOfficeLm(viewModel, form);

        refreshAndAwait(viewModel);

        assertThat(catalog.drafts())
                .extracting(config -> config.id() + " " + config.baseUrl())
                .containsExactly("new-provider http://10.0.0.5:1234/v1");
        assertThat(offered(viewModel)).containsExactly("qwen3:8b");
    }
}
