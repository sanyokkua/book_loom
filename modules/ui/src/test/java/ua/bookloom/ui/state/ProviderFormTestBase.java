package ua.bookloom.ui.state;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.ScriptedModelSettings;

/** What the Providers form tests share: the form over fakes, and the helpers that type and save. */
abstract class ProviderFormTestBase extends SettingsViewModelTestBase {

    static final String OFFICE_KEY = "OFFICE_KEY";

    final ScriptedModelSettings modelSettings = new ScriptedModelSettings();
    final MemorySettings detailSettings = new MemorySettings();
    final TechnicalDetails technical = new TechnicalDetails(detailSettings, Runnable::run, Runnable::run);

    ProviderForm form(final SettingsViewModel viewModel) {
        return onFx(() -> new ProviderForm(
                viewModel, configs, modelSettings, settings, name -> OFFICE_KEY.equals(name), technical, executor));
    }

    static void edit(final ProviderForm form, final UnaryOperator<ProviderDraft> change) {
        onFx(() -> {
            form.edit(change);
            return null;
        });
    }

    static void addOfficeLm(final SettingsViewModel viewModel, final ProviderForm form) {
        onFx(() -> {
            viewModel.startAdding();
            return null;
        });
        edit(
                form,
                draft -> draft.withKind(ProviderKind.OPENAI_COMPATIBLE)
                        .withName("Office LM")
                        .withAddress("http://10.0.0.5:1234/v1"));
    }

    static boolean save(final ProviderForm form) {
        final List<String> ran = new ArrayList<>();
        onFx(() -> {
            form.save(() -> ran.add("then"));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        return !ran.isEmpty();
    }

    static ProviderDraft draftOf(final ProviderForm form) {
        return onFx(() -> form.draft().get());
    }

    static boolean dirtyOf(final ProviderForm form) {
        return onFx(() -> form.dirty().get());
    }
}
