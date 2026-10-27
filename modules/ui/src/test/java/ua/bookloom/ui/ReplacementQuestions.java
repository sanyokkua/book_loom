package ua.bookloom.ui;

import com.google.inject.Module;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.dialog.ContinuePrompt;
import ua.bookloom.ui.dialog.FindBookPrompt;
import ua.bookloom.ui.dialog.ModelPickPrompt;
import ua.bookloom.ui.dialog.OpenAnotherBookPrompt;
import ua.bookloom.ui.dialog.RenamePrompt;
import ua.bookloom.ui.dialog.SamePairPrompt;
import ua.bookloom.ui.dialog.SwitchProviderPrompt;
import ua.bookloom.ui.dialog.TextClipboard;
import ua.bookloom.ui.dialog.TranslateIntoPrompt;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.state.RowOpener;

/** The questions and choosers a test graph replaces with its own fakes; each stays real unless set. */
final class ReplacementQuestions {

    @Nullable
    OpenAnotherBookPrompt prompt;

    @Nullable
    ContinuePrompt continuePrompt;

    @Nullable
    ModelPickPrompt modelPickPrompt;

    @Nullable
    SwitchProviderPrompt switchProviderPrompt;

    @Nullable
    TextClipboard clipboard;

    @Nullable
    RenamePrompt renamePrompt;

    @Nullable
    SamePairPrompt samePairPrompt;

    @Nullable
    TranslateIntoPrompt translateIntoPrompt;

    @Nullable
    FindBookPrompt findBookPrompt;

    @Nullable
    RowOpener rowOpener;

    @Nullable
    ErrorPresenter errorPresenter;

    Module module() {
        return binder -> {
            UiTestBindings.bindIfSet(binder, OpenAnotherBookPrompt.class, prompt);
            UiTestBindings.bindIfSet(binder, ContinuePrompt.class, continuePrompt);
            UiTestBindings.bindIfSet(binder, ModelPickPrompt.class, modelPickPrompt);
            UiTestBindings.bindIfSet(binder, SwitchProviderPrompt.class, switchProviderPrompt);
            UiTestBindings.bindIfSet(binder, TextClipboard.class, clipboard);
            UiTestBindings.bindIfSet(binder, RenamePrompt.class, renamePrompt);
            UiTestBindings.bindIfSet(binder, SamePairPrompt.class, samePairPrompt);
            UiTestBindings.bindIfSet(binder, TranslateIntoPrompt.class, translateIntoPrompt);
            UiTestBindings.bindIfSet(binder, FindBookPrompt.class, findBookPrompt);
            UiTestBindings.bindIfSet(binder, RowOpener.class, rowOpener);
            UiTestBindings.bindIfSet(binder, ErrorPresenter.class, errorPresenter);
        };
    }
}
