package ua.bookloom.ui.screen;

import java.util.Objects;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.BriefEstimate;
import ua.bookloom.ui.state.BriefLock;
import ua.bookloom.ui.state.BriefProject;
import ua.bookloom.ui.state.RunStateSource;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.TranslatingViewModel;
import ua.bookloom.ui.state.WorkflowProgress;

/**
 * What the Book Brief is built from: its view model and catalogue, where Back and Continue lead, and the state the
 * run puts on the screen (the lock, the stored project, the estimate and the run controls).
 *
 * @param viewModel the choices the brief assembles
 * @param messages the catalogue every word comes from
 * @param navigator where Back, Continue and the model link lead
 * @param names how the language boxes name and recognise languages
 * @param settings where the model row reads the chosen model from
 * @param progress where Continue records that the brief step is done
 * @param runs the run-state row, whose translation at work holds Fill in from the book
 * @param lock whether the brief is read-only and whether its languages are locked
 * @param project the open project's stored name and size
 * @param estimate the time estimate under the quality choice
 * @param translating the run controls the read-only banner's Pause uses
 */
record BriefParts(
        BookBriefViewModel viewModel,
        Messages messages,
        Navigator navigator,
        LanguageNames names,
        SettingsViewModel settings,
        WorkflowProgress progress,
        RunStateSource runs,
        BriefLock lock,
        BriefProject project,
        BriefEstimate estimate,
        TranslatingViewModel translating) {

    /** Rejects a missing part. */
    BriefParts {
        Objects.requireNonNull(viewModel, "viewModel");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(navigator, "navigator");
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(runs, "runs");
        Objects.requireNonNull(lock, "lock");
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(estimate, "estimate");
        Objects.requireNonNull(translating, "translating");
    }
}
