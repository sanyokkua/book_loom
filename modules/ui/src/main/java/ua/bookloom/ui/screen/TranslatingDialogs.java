package ua.bookloom.ui.screen;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.ProgressDialog;
import ua.bookloom.ui.dialog.ConfirmDialog;
import ua.bookloom.ui.dialog.NarratorDialog;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.CallHistory;
import ua.bookloom.ui.state.RunStateSource;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The cards the Translating screen asks in or opens: who narrates before a start, whether to stop the run, and the
 * run's kept calls.
 *
 * @param narrator the question asked before a start when the book is told in the first person
 * @param confirm the yes/no question asked before the run is stopped
 * @param modals where "All calls…" opens
 * @param messages the catalogue the calls dialog is worded from
 * @param activities whose clock the calls dialog reads
 */
@Slf4j
record TranslatingDialogs(
        NarratorDialog narrator,
        ConfirmDialog confirm,
        ModalHost modals,
        Messages messages,
        ActivityTracker activities) {

    /**
     * Opens the progress dialog's Calls view on the calls the live card kept, newest first, closed with Close.
     *
     * @param oldestFirst the kept calls, oldest first
     */
    void showCalls(final List<CallSnapshot> oldestFirst) {
        log.info("all calls opened on {} kept calls", oldestFirst.size());
        modals.show(
                ProgressDialog.calls(CallHistory.modelOf(oldestFirst, activities.now()), messages, modals::hide),
                false);
    }

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
