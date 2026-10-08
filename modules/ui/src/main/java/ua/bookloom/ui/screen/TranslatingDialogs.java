package ua.bookloom.ui.screen;

import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.dialog.ConfirmDialog;
import ua.bookloom.ui.dialog.NarratorDialog;
import ua.bookloom.ui.dialog.RetryWithNoteDialog;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The cards the Translating screen asks in: a retry's note, who narrates before a start, and whether to stop the run.
 *
 * @param retry the retry-with-a-note card
 * @param narrator the question asked before a start when the book is told in the first person
 * @param confirm the yes/no question asked before the run is stopped
 */
@Slf4j
record TranslatingDialogs(RetryWithNoteDialog retry, NarratorDialog narrator, ConfirmDialog confirm) {

    /** Stopping ends the run for good (Resume starts a new job), so the person is asked first. */
    void askToStop(final TranslatingViewModel viewModel) {
        log.debug("stop pressed: asking first");
        confirm.ask(ConfirmDialog.Question.STOP_RUN, viewModel::stop);
    }
}
