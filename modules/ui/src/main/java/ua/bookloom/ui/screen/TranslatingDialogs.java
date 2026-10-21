package ua.bookloom.ui.screen;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.dialog.ConfirmDialog;
import ua.bookloom.ui.dialog.NarratorDialog;
import ua.bookloom.ui.dialog.RetryWithNoteDialog;
import ua.bookloom.ui.state.RunStateSource;
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

    /**
     * Stop ends translating for now (Continue translation picks up later), so the person is asked first, with what is
     * kept and what is done again ("74 of 142 segments are done and saved. The 4 being translated now are translated
     * next time.").
     */
    void askToStop(final TranslatingViewModel viewModel, final RunStateSource.StopFigures figures) {
        log.debug(
                "stop pressed at {} of {} with {} in flight: asking first",
                figures.decided(),
                figures.total(),
                figures.inFlight());
        confirm.ask(
                ConfirmDialog.Question.STOP_RUN,
                List.of(figures.decided(), figures.total(), figures.inFlight()),
                viewModel::stop);
    }
}
